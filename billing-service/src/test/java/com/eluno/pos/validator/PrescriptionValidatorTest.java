package com.eluno.pos.validator;

import static org.assertj.core.api.Assertions.*;

import com.eluno.pos.dto.EyePrescriptionDto;
import com.eluno.pos.dto.LensLimitsDto;
import com.eluno.pos.dto.PrescriptionDto;
import com.eluno.pos.dto.ProductVariantDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrescriptionValidatorTest {
    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private final LocalDate date = LocalDate.of(2026, 10, 26);
    private final ProductVariantDto lens =
            new ProductVariantDto(
                    1,
                    "PG",
                    "Progressive",
                    "lens",
                    "90015000",
                    10000,
                    null,
                    false,
                    "progressive",
                    d("1.6"),
                    "standard",
                    new LensLimitsDto(d("-4"), d("4"), d("2"), true, d("0.75"), d("3")));

    private PrescriptionDto rx(EyePrescriptionDto eye, String source) {
        return new PrescriptionDto(
                "RX", 1, date.minusDays(1), date.plusDays(365), source, eye, eye, Map.of());
    }

    @Test
    void rejectsOutOfRangeSphereCylinderAndMissingAdd() {
        var validator = new PrescriptionValidator();
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        rx(
                                                new EyePrescriptionDto(
                                                        d("-6"), d("0"), null, d("2")),
                                                "in_store"),
                                        lens,
                                        date))
                .hasMessageContaining("sphere");
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        rx(
                                                new EyePrescriptionDto(
                                                        d("-2"), d("-3"), 90, d("2")),
                                                "in_store"),
                                        lens,
                                        date))
                .hasMessageContaining("cylinder");
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        rx(
                                                new EyePrescriptionDto(d("-2"), d("0"), null, null),
                                                "in_store"),
                                        lens,
                                        date))
                .hasMessageContaining("ADD");
    }

    @Test
    void preservesExternalAxisWarnsAndRejectsExpiredRx() {
        var validator = new PrescriptionValidator();
        var prescription = rx(new EyePrescriptionDto(d("-2"), d("-1"), 93, d("2")), "external");
        assertThat(validator.validate(prescription, lens, date)).hasSize(2);
        assertThat(prescription.getRight().getAxis()).isEqualTo(93);
        assertThatThrownBy(() -> validator.validate(prescription, lens, date.plusYears(2)))
                .hasMessageContaining("expired");
    }
}
