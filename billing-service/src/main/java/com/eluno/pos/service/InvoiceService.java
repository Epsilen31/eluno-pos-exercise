package com.eluno.pos.service;

import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.OrderLineDto;
import com.eluno.pos.dto.RefundDto;
import com.eluno.pos.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final ShopifyClient shopifyClient;

    public OrderDto getOrder(long orderId) {
        return shopifyClient.getOrder(orderId);
    }

    public String generateInvoiceHtml(long orderId) {
        OrderDto order = getOrder(orderId);
        StringBuilder html =
                new StringBuilder(
                        """
                <!doctype html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Tax invoice</title>
                    <style>
                        body { font: 14px sans-serif; margin: 32px; }
                        table { border-collapse: collapse; width: 100%; }
                        td, th { padding: 10px; border: 1px solid #ccc; text-align: right; }
                        td:first-child { text-align: left; }
                        @media print { body { margin: 0; } }
                    </style>
                </head>
                <body>
                    <h1>ELUNO · TAX INVOICE</h1>
                """);

        html.append("<p>")
                .append(escapeHtml(order.getStore().getName()))
                .append(" · ")
                .append(escapeHtml(order.getInvoiceNo()))
                .append(" · ")
                .append(escapeHtml(order.getCreatedAt().toString()))
                .append("</p>");
        html.append(
                """
                <table>
                    <tr>
                        <th>Item / prescription</th><th>HSN</th><th>Qty</th>
                        <th>MRP incl. GST</th><th>Discount</th><th>Taxable</th>
                        <th>CGST</th><th>SGST</th><th>Total</th>
                    </tr>
                """);

        for (OrderLineDto line : order.getLines()) {
            appendInvoiceLine(html, line);
        }

        html.append("</table><h2>Total ₹")
                .append(MoneyUtils.toDecimalString(order.getTotalPaise()))
                .append("</h2><p>Prices inclusive of GST. Intra-state supply. Shopify order ")
                .append(order.getId())
                .append("</p>");

        for (RefundDto refund : order.getRefunds()) {
            html.append("<p>Credit note ")
                    .append(escapeHtml(refund.getCreditNote()))
                    .append(" · refunded ₹")
                    .append(MoneyUtils.toDecimalString(refund.getAmountPaise()))
                    .append("</p>");
        }
        return html.append("</body></html>").toString();
    }

    private void appendInvoiceLine(StringBuilder html, OrderLineDto line) {
        html.append("<tr><td>")
                .append(escapeHtml(line.getTitle()))
                .append(" ")
                .append(escapeHtml(line.getPrescriptionId()))
                .append("</td><td>")
                .append(escapeHtml(line.getHsn()))
                .append("</td><td>")
                .append(line.getQuantity())
                .append("</td>");

        long taxablePaise = line.getTotalPaise() - line.getCgstPaise() - line.getSgstPaise();
        appendMoneyCell(html, line.getUnitPricePaise() * line.getQuantity());
        appendMoneyCell(html, line.getDiscountPaise());
        appendMoneyCell(html, taxablePaise);
        appendMoneyCell(html, line.getCgstPaise());
        appendMoneyCell(html, line.getSgstPaise());
        appendMoneyCell(html, line.getTotalPaise());
        html.append("</tr>");
    }

    private void appendMoneyCell(StringBuilder html, long amountPaise) {
        html.append("<td>₹").append(MoneyUtils.toDecimalString(amountPaise)).append("</td>");
    }

    private String escapeHtml(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
