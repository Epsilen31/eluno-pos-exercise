#!/usr/bin/env python3
"""Repeatable Must-tier demo using only Python's standard library."""
import argparse
import datetime
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    artifacts = root / ".local" / ("demo-" + uuid.uuid4().hex[:8])
    artifacts.mkdir(parents=True)

    def call(path, body=None, raw=False):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(args.base + path, data=data, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                content = response.read().decode("utf-8")
                return content if raw else json.loads(content)
        except urllib.error.HTTPError as exc:
            raise RuntimeError("HTTP %s: %s" % (exc.code, exc.read().decode())) from exc

    def money(value):
        return "%s.%02d" % (value // 100, value % 100)

    customer = call("/api/customers/lookup?" + urllib.parse.urlencode({"phone": "+919000000005"}))
    assert customer["prescription"]["id"] == "RX5007"
    print("1. Customer: %s; latest valid prescription: %s" % (customer["customer"]["name"], customer["prescription"]["id"]))
    cart = dict(phone="+919000000005", storeCode="HSR", staffId="S105", channel="walk_in", paymentMethod="upi",
                pairs=[dict(pairRef="pair1", frameVariantId=91000003, lensVariantId=91000083)])
    invalid = dict(cart, phone="+919000000001")
    try:
        call("/api/cart/quote", invalid)
    except RuntimeError as exc:
        assert "ADD" in str(exc), str(exc)
        print("2. Invalid progressive choice blocked: missing ADD.")
    else:
        raise AssertionError("Expected clinical validation failure")
    stock_path = "/api/stock?storeCode=HSR&variantId=91000003"
    stock_before = call(stock_path)["available"]
    quote = call("/api/cart/quote", cart)
    assert quote["taxablePaise"] + quote["cgstPaise"] + quote["sgstPaise"] == quote["totalPaise"]
    print("3. Offers: " + ", ".join(o["offerId"] for o in quote["offers"] if o["applied"]))
    print("   Gross INR %s; discounts %s; payable %s" % (money(quote["grossPaise"]), money(quote["discountPaise"]), money(quote["totalPaise"])))
    reference = "demo-" + uuid.uuid4().hex[:12]
    payload = dict(reference=reference, cart=cart, expectedTotalPaise=quote["totalPaise"])
    sale = call("/api/checkouts", payload)
    assert sale["state"] == "COMPLETED", sale
    order_id = sale["orderId"]
    assert call(stock_path)["available"] == stock_before - 1
    print("4. Payment captured; Shopify order %s; invoice %s; frame stock decremented." % (order_id, sale["invoiceNo"]))
    repeat = call("/api/checkouts", payload)
    assert repeat["orderId"] == order_id and repeat["externalId"] == sale["externalId"]
    print("5. Repeated checkout returns the same order and charge.")
    order = call("/api/orders/%s" % order_id)
    invoice = call("/api/orders/%s/invoice" % order_id, raw=True)
    (artifacts / "invoice.html").write_text(invoice, encoding="utf-8")
    refund_payload = dict(reference=reference + "-return", staffId="S105", reasonCode="CHANGED_MIND")
    refund = call("/api/orders/%s/refund" % order_id, refund_payload)
    assert refund["state"] == "COMPLETED", refund
    assert call(stock_path)["available"] == stock_before
    assert call("/api/orders/%s/refund" % order_id, refund_payload)["invoiceNo"] == refund["invoiceNo"]
    print("6. Full refund recorded: %s; frame stock restored; lens never restocked." % refund["invoiceNo"])
    # Fixed IST offset avoids requiring external tzdata on Windows/Python 3.8.
    bill_date = datetime.datetime.fromisoformat(order["createdAt"].replace("Z", "+00:00")).astimezone(datetime.timezone(datetime.timedelta(hours=5, minutes=30))).date()
    report = call("/api/reports/day-end?" + urllib.parse.urlencode(dict(storeCode="HSR", date=str(bill_date))))
    assert not report["reconciliationIssues"], report
    print("7. Shopify day-end %s: %s bill(s), sales INR %s, refunds %s, net %s" %
          (bill_date, report["billCount"], money(report["salesPaise"]), money(report["refundsPaise"]), money(report["netPaise"])))
    for name, value in dict(quote=quote, sale=sale, refund=refund, report=report).items():
        (artifacts / (name + ".json")).write_text(json.dumps(value, indent=2), encoding="utf-8")
    print("Demo passed. Invoice and JSON artifacts: " + str(artifacts))


if __name__ == "__main__":
    main()
