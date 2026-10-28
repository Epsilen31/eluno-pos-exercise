package com.eluno.pos.service;

import static org.assertj.core.api.Assertions.*;

import com.eluno.pos.dto.TaxBreakdownDto;
import com.eluno.pos.dto.TaxRateDto;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.util.MoneyUtils;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxServiceTest {
    private final TaxService calculator = new TaxService();

    @Test
    void sampleInvoiceMatchesExactly() {
        assertThat(calculator.calculateIncludedTax(1099800, 1800))
                .isEqualTo(new TaxBreakdownDto(932034, 83883, 83883, 1800));
        assertThat(calculator.calculateIncludedTax(807415, 500))
                .isEqualTo(new TaxBreakdownDto(768967, 19224, 19224, 500));
    }

    @Test
    void oddPaisaGoesToCgstAndEveryLineReconciles() {
        for (int amount = 0; amount < 25000; amount++) {
            var tax = calculator.calculateIncludedTax(amount, 500);
            assertThat(tax.getTaxablePaise() + tax.getTotalPaise()).isEqualTo(amount);
            assertThat(tax.getCgstPaise() - tax.getSgstPaise()).isBetween(0L, 1L);
        }
    }

    @Test
    void usesEffectiveDateAndRejectsMissingOrOverlappingRates() {
        var rates =
                List.of(
                        new TaxRateDto(
                                "lens", 1200, LocalDate.of(2017, 7, 1), LocalDate.of(2025, 9, 21)),
                        new TaxRateDto("lens", 500, LocalDate.of(2025, 9, 22), null));
        assertThat(
                        calculator
                                .calculate(10000, "lens", LocalDate.of(2025, 9, 21), rates)
                                .getRateBps())
                .isEqualTo(1200);
        assertThat(
                        calculator
                                .calculate(10000, "lens", LocalDate.of(2025, 9, 22), rates)
                                .getRateBps())
                .isEqualTo(500);
        assertThatThrownBy(() -> calculator.calculate(10000, "unknown", LocalDate.now(), rates))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(
                        () ->
                                calculator.calculate(
                                        10000,
                                        "lens",
                                        LocalDate.of(2026, 1, 1),
                                        List.of(rates.get(1), rates.get(1))))
                .isInstanceOf(BillingValidationException.class);
    }

    @Test
    void parsesWithoutFloatingPointAndAllocatesRemainder() {
        assertThat(MoneyUtils.fromDecimalString("19072.15")).isEqualTo(1907215);
        assertThat(MoneyUtils.toDecimalString(1)).isEqualTo("0.01");
        assertThat(MoneyUtils.allocateProportionally(2, new long[] {1, 1, 1}))
                .containsExactly(1, 1, 0);
        assertThatThrownBy(() -> MoneyUtils.fromDecimalString("1.001"))
                .isInstanceOf(ArithmeticException.class);
    }
}
