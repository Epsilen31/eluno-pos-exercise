package com.eluno.pos.service;

import static org.assertj.core.api.Assertions.*;

import com.eluno.pos.dto.CartLineDto;
import com.eluno.pos.dto.CustomerDto;
import com.eluno.pos.dto.DiscountAllocationDto;
import com.eluno.pos.dto.OfferDecisionDto;
import com.eluno.pos.dto.OfferRuleDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.repository.JsonRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfferServiceTest {
    private final OfferService engine = new OfferService();
    private final List<OfferRuleDto> rules =
            new JsonRuleRepository("../starter-kit/data", new ObjectMapper()).getOffers();
    private final LocalDate date = LocalDate.of(2026, 10, 26);

    private ProductVariantDto frame(long price) {
        return new ProductVariantDto(
                1, "frame", "Frame", "frame", "90031900", price, 1L, true, null, null, null, null);
    }

    private ProductVariantDto lens(long price) {
        return new ProductVariantDto(
                2,
                "lens",
                "Lens",
                "lens",
                "90015000",
                price,
                null,
                false,
                "progressive",
                new BigDecimal("1.60"),
                "blue_cut",
                null);
    }

    private CustomerDto customer(String... tags) {
        return new CustomerDto(1, "Test", "+919000000005", Set.of(tags));
    }

    private List<CartLineDto> pair(long frame, long lens) {
        return List.of(
                new CartLineDto(frame(frame), "1", null), new CartLineDto(lens(lens), "1", null));
    }

    @Test
    void picksBestCombinationAndExplainsExclusiveOffer() {
        var result =
                engine.evaluate(
                        pair(1099800, 949900),
                        customer("priority", "campaign:progressive_oct26"),
                        date,
                        rules,
                        null,
                        null,
                        null);
        // stackable blue-cut + priority + bundle beats exclusive frame 10% or campaign 15%.
        assertThat(Arrays.stream(result.getDiscounts()).sum()).isEqualTo(180000);
        assertThat(
                        result.getDecisions().stream()
                                .filter(OfferDecisionDto::isApplied)
                                .map(OfferDecisionDto::getOfferId))
                .containsExactlyInAnyOrder("FREE_BLUECUT_PGX", "PRIORITY500", "BUNDLE300");
        assertThat(
                        result.getDecisions().stream()
                                .filter(d -> d.getOfferId().equals("MONSOON20"))
                                .findFirst()
                                .orElseThrow()
                                .getReason())
                .contains("Inactive");
    }

    @Test
    void secondPairUsesCheapestLensAndAllocationsReconcile() {
        var lines =
                List.of(
                        new CartLineDto(frame(100000), "a", null),
                        new CartLineDto(lens(400000), "a", null),
                        new CartLineDto(frame(100000), "b", null),
                        new CartLineDto(lens(200000), "b", null));
        var result = engine.evaluate(lines, customer("priority"), date, rules, null, null, null);
        var second =
                result.getDecisions().stream()
                        .filter(d -> d.getOfferId().equals("SECONDPAIR50"))
                        .findFirst()
                        .orElseThrow();
        assertThat(second.getAmountPaise()).isEqualTo(100000);
        assertThat(result.getAllocations().get(3))
                .contains(new DiscountAllocationDto("SECONDPAIR50", 100000));
        for (int i = 0; i < lines.size(); i++)
            assertThat(
                            result.getAllocations().get(i).stream()
                                    .mapToLong(DiscountAllocationDto::getAmountPaise)
                                    .sum())
                    .isEqualTo(result.getDiscounts()[i]);
    }

    @Test
    void rejectsUnauthorizedManagerAndCapsManualPercentage() {
        assertThatThrownBy(
                        () ->
                                engine.evaluate(
                                        pair(100000, 100000),
                                        customer(),
                                        date,
                                        rules,
                                        1000,
                                        new StaffDto("S105", "sales", false),
                                        "SAVE_SALE"))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(
                        () ->
                                engine.evaluate(
                                        pair(100000, 100000),
                                        customer(),
                                        date,
                                        rules,
                                        1001,
                                        new StaffDto("S108", "store_manager", true),
                                        "SAVE_SALE"))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(
                        () ->
                                engine.evaluate(
                                        pair(100000, 100000),
                                        customer(),
                                        date,
                                        rules,
                                        1000,
                                        new StaffDto("S108", "store_manager", true),
                                        null))
                .isInstanceOf(BillingValidationException.class);
    }

    @Test
    void expiredOffersAndUnmatchedTagsDoNotApply() {
        var result =
                engine.evaluate(
                        List.of(new CartLineDto(frame(500000), "1", null)),
                        customer(),
                        LocalDate.of(2027, 1, 1),
                        rules,
                        null,
                        null,
                        null);
        assertThat(Arrays.stream(result.getDiscounts()).sum()).isZero();
    }

    @Test
    void flatDiscountCannotMakeAnyLineNegativeAndCapIsPerOffer() {
        var result =
                engine.evaluate(pair(1, 1), customer("priority"), date, rules, null, null, null);
        assertThat(result.getDiscounts()).containsExactly(1, 1);
        var expensive =
                engine.evaluate(
                        List.of(new CartLineDto(frame(5000000), "1", null)),
                        customer(),
                        date,
                        rules,
                        null,
                        null,
                        null);
        assertThat(expensive.getDiscounts()).containsExactly(200000);
    }
}
