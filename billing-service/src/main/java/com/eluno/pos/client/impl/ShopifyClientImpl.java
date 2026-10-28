package com.eluno.pos.client.impl;

import com.eluno.pos.client.ExternalApiClient;
import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.dto.CustomerDto;
import com.eluno.pos.dto.EyePrescriptionDto;
import com.eluno.pos.dto.LensLimitsDto;
import com.eluno.pos.dto.OfferDecisionDto;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.OrderLineDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.QuoteResponse;
import com.eluno.pos.dto.RefundDto;
import com.eluno.pos.dto.RefundPlanDto;
import com.eluno.pos.dto.RefundedLineDto;
import com.eluno.pos.dto.SaleTransactionDto;
import com.eluno.pos.dto.StoreDto;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.util.MoneyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Only this boundary knows Shopify REST payloads and decimal money strings. */
@Component
@RequiredArgsConstructor
public class ShopifyClientImpl implements ShopifyClient {
    private static final String API = "/admin/api/2026-10/";
    private final ExternalApiClient externalApiClient;

    @Override
    public CustomerDto findCustomerByPhone(String phone) {
        var matches =
                externalApiClient
                        .get(
                                API
                                        + "customers/search.json?query="
                                        + encodeQueryParameter("phone:" + phone))
                        .path("customers");
        if (!(matches.size() == 1)) {
            throw new BillingValidationException(
                    "Expected one customer for phone; check number or resolve duplicate customer records");
        }
        var node = matches.get(0);
        var tags = new HashSet<String>();
        for (String tag : node.path("tags").asText().split(","))
            if (!tag.isBlank()) tags.add(tag.trim());
        return new CustomerDto(
                node.path("id").asLong(),
                (node.path("first_name").asText() + " " + node.path("last_name").asText()).trim(),
                node.path("phone").asText(),
                tags);
    }

    @Override
    public List<StoreDto> getStores() {
        var result = new ArrayList<StoreDto>();
        for (var node : externalApiClient.get(API + "locations.json").path("locations")) {
            var metadata = node.path("eluno");
            if (node.path("active").asBoolean() && "store".equals(metadata.path("kind").asText()))
                result.add(
                        new StoreDto(
                                node.path("id").asLong(),
                                metadata.path("store_code").asText(),
                                node.path("name").asText(),
                                metadata.path("invoice_prefix").asText()));
        }
        return result;
    }

    @Override
    public List<ProductVariantDto> getCatalog() {
        var result = new ArrayList<ProductVariantDto>();
        for (var productNode :
                externalApiClient.get(API + "products.json?limit=250").path("products")) {
            if (!"active".equals(productNode.path("status").asText())) continue;
            for (var variantNode : productNode.path("variants")) {
                var metadata = variantNode.path("eluno");
                var limits = metadata.path("rx_limits");
                result.add(
                        new ProductVariantDto(
                                variantNode.path("id").asLong(),
                                variantNode.path("sku").asText(),
                                productNode.path("title").asText()
                                        + " "
                                        + variantNode.path("title").asText(),
                                metadata.path("line_type").asText(),
                                metadata.path("hsn").asText(),
                                MoneyUtils.fromDecimalString(variantNode.path("price").asText()),
                                variantNode.hasNonNull("inventory_item_id")
                                        ? variantNode.get("inventory_item_id").asLong()
                                        : null,
                                metadata.path("rx_able").asBoolean(),
                                readOptionalText(metadata, "lens_type"),
                                metadata.has("index") ? metadata.get("index").decimalValue() : null,
                                readOptionalText(metadata, "coating"),
                                limits.isMissingNode()
                                        ? null
                                        : new LensLimitsDto(
                                                limits.path("sph_min").decimalValue(),
                                                limits.path("sph_max").decimalValue(),
                                                limits.path("cyl_abs_max").decimalValue(),
                                                limits.path("add_required").asBoolean(),
                                                limits.hasNonNull("add_min")
                                                        ? limits.get("add_min").decimalValue()
                                                        : null,
                                                limits.hasNonNull("add_max")
                                                        ? limits.get("add_max").decimalValue()
                                                        : null)));
            }
        }
        return result;
    }

    @Override
    public int getAvailableStock(ProductVariantDto frame, StoreDto store) {
        var rows =
                externalApiClient
                        .get(
                                API
                                        + "inventory_levels.json?inventory_item_ids="
                                        + frame.getInventoryItemId()
                                        + "&location_ids="
                                        + store.getId())
                        .path("inventory_levels");
        return rows.isEmpty() ? 0 : rows.get(0).path("available").asInt();
    }

    @Override
    public OrderDto createOrder(
            QuoteResponse quote, String reference, String invoice, String chargeId) {
        var lines = new ArrayList<Map<String, Object>>();
        var offerIds =
                quote.getOffers().stream()
                        .filter(OfferDecisionDto::isApplied)
                        .map(OfferDecisionDto::getOfferId)
                        .toList();
        var discountApplications = new ArrayList<Map<String, Object>>();
        for (String id : offerIds) {
            long amount =
                    quote.getOffers().stream()
                            .filter(d -> d.getOfferId().equals(id))
                            .mapToLong(OfferDecisionDto::getAmountPaise)
                            .sum();
            discountApplications.add(
                    Map.of(
                            "type",
                            "manual",
                            "title",
                            id,
                            "value",
                            MoneyUtils.toDecimalString(amount),
                            "value_type",
                            "fixed_amount",
                            "allocation_method",
                            "across",
                            "target_selection",
                            "explicit",
                            "target_type",
                            "line_item"));
        }
        for (var priced : quote.getLines()) {
            var item = priced.getItem();
            var variantNode = item.getVariant();
            var properties = new ArrayList<Map<String, Object>>();
            properties.add(createAttribute("line_type", variantNode.getLineType()));
            properties.add(createAttribute("hsn", variantNode.getHsn()));
            properties.add(createAttribute("pair_ref", item.getPairRef()));
            if (item.getPrescription() != null) {
                var rx = item.getPrescription();
                properties.add(createAttribute("rx_id", rx.getId()));
                properties.add(createAttribute("for_customer_id", rx.getCustomerId()));
                properties.add(
                        createAttribute(
                                "lens_config",
                                variantNode.getLensType()
                                        + "/"
                                        + variantNode.getIndex()
                                        + "/"
                                        + variantNode.getCoating()));
                properties.add(createAttribute("rx_od", formatEyePrescription(rx.getRight())));
                properties.add(createAttribute("rx_os", formatEyePrescription(rx.getLeft())));
                properties.add(createAttribute("rx_pd", rx.getPd().toString()));
                properties.add(createAttribute("rx_valid_until", rx.getValidUntil()));
            }
            var allocations =
                    priced.getAllocations().stream()
                            .map(
                                    a ->
                                            Map.<String, Object>of(
                                                    "amount",
                                                    MoneyUtils.toDecimalString(a.getAmountPaise()),
                                                    "discount_application_index",
                                                    offerIds.indexOf(a.getOfferId())))
                            .toList();
            var tax = priced.getTax();
            lines.add(
                    Map.of(
                            "variant_id",
                            variantNode.getId(),
                            "quantity",
                            1,
                            "price",
                            MoneyUtils.toDecimalString(variantNode.getPricePaise()),
                            "total_discount",
                            MoneyUtils.toDecimalString(priced.getDiscountPaise()),
                            "tax_lines",
                            List.of(
                                    createTaxLine("CGST", tax.getCgstPaise(), tax.getRateBps()),
                                    createTaxLine("SGST", tax.getSgstPaise(), tax.getRateBps())),
                            "discount_allocations",
                            allocations,
                            "properties",
                            properties,
                            "title",
                            variantNode.getTitle()));
        }
        var attributes =
                new ArrayList<>(
                        List.of(
                                createAttribute("invoice_no", invoice),
                                createAttribute("checkout_ref", reference),
                                createAttribute("store_code", quote.getStore().getCode()),
                                createAttribute("channel", quote.getChannel()),
                                createAttribute("staff_id", quote.getStaffId()),
                                createAttribute("payment_method", quote.getPaymentMethod())));
        if (quote.getManagerId() != null) {
            attributes.add(createAttribute("approver_id", quote.getManagerId()));
            attributes.add(createAttribute("reason_code", quote.getReasonCode()));
        }
        var payload =
                Map.of(
                        "taxes_included",
                        true,
                        "location_id",
                        quote.getStore().getId(),
                        "inventory_behaviour",
                        "decrement_obeying_policy",
                        "source_name",
                        "eluno-pos",
                        "customer",
                        Map.of("id", quote.getCustomer().getId()),
                        "note_attributes",
                        attributes,
                        "line_items",
                        lines,
                        "discount_applications",
                        discountApplications,
                        "transactions",
                        List.of(
                                Map.of(
                                        "kind",
                                        "sale",
                                        "status",
                                        "success",
                                        "gateway",
                                        "cash".equals(quote.getPaymentMethod())
                                                ? "cash"
                                                : "razorpay_terminal",
                                        "amount",
                                        MoneyUtils.toDecimalString(quote.getTotalPaise()),
                                        "authorization",
                                        chargeId)));
        return parseOrder(
                externalApiClient.post(API + "orders.json", Map.of("order", payload)).path("order"),
                quote.getStore());
    }

    @Override
    public OrderDto getOrder(long id) {
        var node = externalApiClient.get(API + "orders/" + id + ".json").path("order");
        var store =
                getStores().stream()
                        .filter(s -> s.getId() == node.path("location_id").asLong())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BillingValidationException(
                                                "Order belongs to an unknown store"));
        return parseOrder(node, store);
    }

    @Override
    public List<OrderDto> getOrders(StoreDto store) {
        // The exercise mock has no cursor pagination. A high limit avoids its default 50-row
        // truncation.
        var result = new ArrayList<OrderDto>();
        var nodes =
                externalApiClient
                        .get(
                                API
                                        + "orders.json?status=any&limit=100000&location_id="
                                        + store.getId())
                        .path("orders");
        if (!(nodes.size() < 100000)) {
            throw new BillingValidationException(
                    "Mock order scan limit reached; refusing an incomplete report/reconciliation");
        }
        for (var node : nodes) result.add(parseOrder(node, store));
        return result;
    }

    @Override
    public RefundDto createRefund(RefundPlanDto plan, String terminalRefundId) {
        var order = plan.getOrder();
        var lines =
                order.getLines().stream()
                        .map(
                                l -> {
                                    var result = new LinkedHashMap<String, Object>();
                                    result.put("line_item_id", l.getId());
                                    result.put("quantity", l.getQuantity());
                                    result.put(
                                            "restock_type",
                                            "frame".equals(l.getLineType())
                                                    ? "return"
                                                    : "no_restock");
                                    if ("frame".equals(l.getLineType()))
                                        result.put("location_id", order.getStore().getId());
                                    return result;
                                })
                        .toList();
        var attributes =
                List.of(
                        createAttribute("credit_note_no", plan.getCreditNote()),
                        createAttribute("parent_invoice_no", order.getInvoiceNo()),
                        createAttribute("reason_code", plan.getReason()),
                        createAttribute("staff_id", plan.getStaffId()),
                        createAttribute("terminal_refund_id", terminalRefundId));
        var payload =
                Map.of(
                        "note",
                        plan.getReason(),
                        "note_attributes",
                        attributes,
                        "refund_line_items",
                        lines,
                        "transactions",
                        List.of(
                                Map.of(
                                        "kind",
                                        "refund",
                                        "amount",
                                        MoneyUtils.toDecimalString(plan.getAmountPaise()),
                                        "gateway",
                                        plan.getSale().getGateway(),
                                        "parent_id",
                                        plan.getSale().getId())));
        // No calculate call is necessary: full refunds reverse the stored invoice amounts exactly.
        return parseRefund(
                externalApiClient
                        .post(
                                API + "orders/" + order.getId() + "/refunds.json",
                                Map.of("refund", payload))
                        .path("refund"));
    }

    private OrderDto parseOrder(JsonNode node, StoreDto store) {
        var lines = new ArrayList<OrderLineDto>();
        var sales = new ArrayList<SaleTransactionDto>();
        var refunds = new ArrayList<RefundDto>();
        for (var lineItem : node.path("line_items")) {
            long cg = 0, sg = 0;
            for (var t : lineItem.path("tax_lines")) {
                if ("CGST".equals(t.path("title").asText())) cg += readMoneyPaise(t, "price");
                else if ("SGST".equals(t.path("title").asText())) sg += readMoneyPaise(t, "price");
                else
                    throw new BillingValidationException(
                            "Unsupported tax line on Shopify order; report requires review");
            }
            lines.add(
                    new OrderLineDto(
                            lineItem.path("id").asLong(),
                            lineItem.path("variant_id").asLong(),
                            lineItem.path("title").asText(lineItem.path("sku").asText()),
                            readAttribute(lineItem.path("properties"), "line_type"),
                            readAttribute(lineItem.path("properties"), "hsn"),
                            readAttribute(lineItem.path("properties"), "rx_id"),
                            lineItem.path("quantity").asInt(),
                            readMoneyPaise(lineItem, "price"),
                            readMoneyPaise(lineItem, "total_discount"),
                            cg,
                            sg));
        }
        for (var t : node.path("transactions"))
            if ("sale".equals(t.path("kind").asText())
                    && "success".equals(t.path("status").asText()))
                sales.add(
                        new SaleTransactionDto(
                                t.path("id").asLong(),
                                t.path("gateway").asText(),
                                t.path("authorization").asText(),
                                readMoneyPaise(t, "amount")));
        for (var r : node.path("refunds")) refunds.add(parseRefund(r));
        var attributes = node.path("note_attributes");
        return new OrderDto(
                node.path("id").asLong(),
                store,
                readAttribute(attributes, "invoice_no"),
                readAttribute(attributes, "checkout_ref"),
                OffsetDateTime.parse(node.path("created_at").asText()),
                readAttribute(attributes, "channel"),
                readAttribute(attributes, "payment_method"),
                readMoneyPaise(node, "subtotal_price"),
                readMoneyPaise(node, "total_discounts"),
                readMoneyPaise(node, "total_tax"),
                readMoneyPaise(node, "total_price"),
                lines,
                sales,
                refunds,
                node.hasNonNull("cancelled_at"));
    }

    private RefundDto parseRefund(JsonNode node) {
        var lines = new ArrayList<RefundedLineDto>();
        long amount = 0;
        for (var l : node.path("refund_line_items"))
            lines.add(
                    new RefundedLineDto(
                            l.path("line_item_id").asLong(),
                            l.path("quantity").asInt(),
                            readMoneyPaise(l, "subtotal"),
                            readMoneyPaise(l, "total_tax")));
        for (var t : node.path("transactions"))
            if ("success".equals(t.path("status").asText())
                    && "refund".equals(t.path("kind").asText()))
                amount += readMoneyPaise(t, "amount");
        return new RefundDto(
                node.path("id").asLong(),
                readAttribute(node.path("note_attributes"), "credit_note_no"),
                OffsetDateTime.parse(node.path("created_at").asText()),
                lines,
                amount);
    }

    private static Map<String, Object> createTaxLine(String title, long price, int rateBps) {
        return Map.of(
                "title",
                title,
                "price",
                MoneyUtils.toDecimalString(price),
                "rate",
                BigDecimal.valueOf(rateBps).divide(BigDecimal.valueOf(20000)));
    }

    private static Map<String, Object> createAttribute(String name, Object value) {
        return Map.of("name", name, "value", String.valueOf(value));
    }

    private static String formatEyePrescription(EyePrescriptionDto metadata) {
        return "sph="
                + metadata.getSphere()
                + ";cyl="
                + metadata.getCylinder()
                + ";axis="
                + metadata.getAxis()
                + ";add="
                + metadata.getAdd();
    }

    private static long readMoneyPaise(JsonNode node, String key) {
        return MoneyUtils.fromDecimalString(node.path(key).asText("0.00"));
    }

    private static String readOptionalText(JsonNode node, String key) {
        return node.hasNonNull(key) ? node.get(key).asText() : null;
    }

    private static String encodeQueryParameter(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String readAttribute(JsonNode array, String key) {
        for (var a : array)
            if (key.equals(a.path("name").asText())) return a.path("value").asText();
        return "";
    }
}
