package com.eluno.pos.service;

import com.eluno.pos.dto.DayEndReportResponse;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.OrderLineDto;
import com.eluno.pos.dto.RefundedLineDto;
import com.eluno.pos.dto.ReportSplitDto;
import com.eluno.pos.dto.SaleTransactionDto;
import com.eluno.pos.dto.StoreDto;
import com.eluno.pos.util.MoneyUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class DayEndReportService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public DayEndReportResponse calculate(
            StoreDto store, LocalDate date, List<OrderDto> orders, long unresolved) {
        long count = 0,
                gross = 0,
                discounts = 0,
                sales = 0,
                tax = 0,
                refunds = 0,
                refundTaxable = 0,
                refundTax = 0;
        var payments = new TreeMap<String, long[]>();
        var channels = new TreeMap<String, long[]>();
        var issues = new ArrayList<String>();
        for (var order : orders) {
            if (!order.isCancelled()
                    && date.equals(order.getCreatedAt().atZoneSameInstant(IST).toLocalDate())) {
                count++;
                gross += order.getGrossPaise();
                discounts += order.getDiscountPaise();
                sales += order.getTotalPaise();
                tax += order.getTaxPaise();
                long captured =
                        order.getSales().stream()
                                .mapToLong(SaleTransactionDto::getAmountPaise)
                                .sum();
                if (captured != order.getTotalPaise())
                    issues.add(
                            order.getInvoiceNo() + ": captured sale does not match invoice total");
                long lineTotal =
                        order.getLines().stream().mapToLong(OrderLineDto::getTotalPaise).sum();
                long lineTax =
                        order.getLines().stream()
                                .mapToLong(l -> l.getCgstPaise() + l.getSgstPaise())
                                .sum();
                if (lineTotal != order.getTotalPaise()
                        || lineTax != order.getTaxPaise()
                        || order.getGrossPaise() - order.getDiscountPaise()
                                != order.getTotalPaise())
                    issues.add(
                            order.getInvoiceNo() + ": Shopify line/header totals do not reconcile");
                payments.computeIfAbsent(label(order.getPaymentMethod()), k -> new long[2])[0] +=
                        captured;
                channels.computeIfAbsent(label(order.getChannel()), k -> new long[2])[0] +=
                        order.getTotalPaise();
            }
            // Scan all store orders: a refund today may refer to an invoice from another date.
            for (var refund : order.getRefunds())
                if (date.equals(refund.getCreatedAt().atZoneSameInstant(IST).toLocalDate())) {
                    refunds += refund.getAmountPaise();
                    long rt =
                            refund.getLines().stream()
                                    .mapToLong(RefundedLineDto::getTaxablePaise)
                                    .sum();
                    long rx =
                            refund.getLines().stream()
                                    .mapToLong(RefundedLineDto::getTaxPaise)
                                    .sum();
                    refundTaxable += rt;
                    refundTax += rx;
                    if (rt + rx != refund.getAmountPaise())
                        issues.add(
                                refund.getCreditNote()
                                        + ": refund lines do not match refund transaction");
                    payments.computeIfAbsent(label(order.getPaymentMethod()), k -> new long[2])[
                                    1] +=
                            refund.getAmountPaise();
                    channels.computeIfAbsent(label(order.getChannel()), k -> new long[2])[1] +=
                            refund.getAmountPaise();
                }
        }
        if (unresolved > 0)
            issues.add(
                    unresolved
                            + " unresolved local operation(s); inspect /api/operations before finance sign-off");
        return new DayEndReportResponse(
                store.getCode(),
                date,
                IST.getId(),
                count,
                gross,
                discounts,
                sales,
                sales - tax,
                tax,
                refunds,
                refundTaxable,
                refundTax,
                sales - refunds,
                sales - tax - refundTaxable,
                tax - refundTax,
                count == 0 ? 0 : MoneyUtils.multiplyAndDivideWithRounding(sales, 1, count),
                split(payments),
                split(channels),
                unresolved,
                issues);
    }

    private static String label(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }

    private static Map<String, ReportSplitDto> split(Map<String, long[]> values) {
        var result = new TreeMap<String, ReportSplitDto>();
        values.forEach((key, v) -> result.put(key, new ReportSplitDto(v[0], v[1], v[0] - v[1])));
        return result;
    }
}
