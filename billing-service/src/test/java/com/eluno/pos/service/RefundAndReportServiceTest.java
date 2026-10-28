package com.eluno.pos.service;

import static org.assertj.core.api.Assertions.*;

import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.OrderLineDto;
import com.eluno.pos.dto.RefundDto;
import com.eluno.pos.dto.RefundedLineDto;
import com.eluno.pos.dto.SaleTransactionDto;
import com.eluno.pos.dto.StoreDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundAndReportServiceTest {
    private final StoreDto store = new StoreDto(1, "HSR", "HSR", "ELU/HSR/FY26/");

    private OrderDto original(List<RefundDto> refunds) {
        var line =
                new OrderLineDto(
                        1, 1, "Frame", "frame", "90031900", "", 1, 1099800, 0, 83883, 83883);
        return new OrderDto(
                1,
                store,
                "ELU/HSR/FY26/00001",
                "sale-test",
                OffsetDateTime.parse("2026-10-01T20:00:00Z"),
                "walk_in",
                "upi",
                1099800,
                0,
                167766,
                1099800,
                List.of(line),
                List.of(new SaleTransactionDto(1, "razorpay_terminal", "charge", 1099800)),
                refunds,
                false);
    }

    @Test
    void fullRefundReversesStoredTaxWithoutRecalculation() {
        var plan =
                new RefundService()
                        .calculateFullRefund(original(List.of()), "CN1", "RETURN", "S105");
        assertThat(plan.getAmountPaise()).isEqualTo(1099800);
        assertThat(plan.getTaxablePaise()).isEqualTo(932034);
        assertThat(plan.getCgstPaise()).isEqualTo(83883);
        assertThat(plan.getTaxablePaise() + plan.getCgstPaise() + plan.getSgstPaise())
                .isEqualTo(plan.getAmountPaise());
    }

    @Test
    void rejectsAlreadyRefundedOrder() {
        var refund = new RefundDto(1, "CN1", OffsetDateTime.now(), List.of(), 1099800);
        assertThatThrownBy(
                        () ->
                                new RefundService()
                                        .calculateFullRefund(
                                                original(List.of(refund)), "CN2", "RETURN", "S105"))
                .hasMessageContaining("already");
    }

    @Test
    void reportUsesIstAndCountsRefundOnItsOwnDate() {
        var refund =
                new RefundDto(
                        1,
                        "CN1",
                        OffsetDateTime.parse("2026-10-03T01:00:00+05:30"),
                        List.of(new RefundedLineDto(1, 1, 932034, 167766)),
                        1099800);
        var order = original(List.of(refund));
        var calculator = new DayEndReportService();
        var saleDay = calculator.calculate(store, LocalDate.of(2026, 10, 2), List.of(order), 0);
        assertThat(saleDay.getBillCount()).isEqualTo(1);
        assertThat(saleDay.getRefundsPaise()).isZero();
        var refundDay = calculator.calculate(store, LocalDate.of(2026, 10, 3), List.of(order), 0);
        assertThat(refundDay.getBillCount()).isZero();
        assertThat(refundDay.getNetPaise()).isEqualTo(-1099800);
        assertThat(refundDay.getNetTaxPaise()).isEqualTo(-167766);
        assertThat(refundDay.getPaymentMethods().get("upi").getNetPaise()).isEqualTo(-1099800);
        assertThat(refundDay.getReconciliationIssues()).isEmpty();
    }
}
