package com.eluno.pos.service;

import com.eluno.pos.dto.CartLineDto;
import com.eluno.pos.dto.CustomerDto;
import com.eluno.pos.dto.DiscountAllocationDto;
import com.eluno.pos.dto.OfferConditionDto;
import com.eluno.pos.dto.OfferDecisionDto;
import com.eluno.pos.dto.OfferEvaluationResultDto;
import com.eluno.pos.dto.OfferRuleDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.util.MoneyUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Select the best allowed discount combination. Non-stackable offers are exclusive across the cart.
 */
@Service
public class OfferService {
    public OfferEvaluationResultDto evaluate(
            List<CartLineDto> cartLines,
            CustomerDto customer,
            LocalDate billingDate,
            List<OfferRuleDto> configuredOffers,
            Integer managerDiscountBps,
            StaffDto manager,
            String reasonCode) {
        if (configuredOffers.size() > 16) {
            throw new BillingValidationException(
                    "Offer configuration exceeds bounded evaluation limit (16)");
        }

        List<OfferRuleDto> sortedOffers =
                configuredOffers.stream()
                        .sorted(
                                Comparator.comparingInt(OfferRuleDto::getPriority)
                                        .thenComparing(OfferRuleDto::getId))
                        .toList();
        List<OfferRuleDto> eligibleOffers = new ArrayList<>();
        Map<String, String> rejectionReasons = new LinkedHashMap<>();

        for (OfferRuleDto offer : sortedOffers) {
            String rejectionReason =
                    findIneligibilityReason(
                            offer,
                            cartLines,
                            customer,
                            billingDate,
                            managerDiscountBps,
                            manager,
                            reasonCode);
            if (rejectionReason == null) {
                eligibleOffers.add(offer);
            } else {
                rejectionReasons.put(offer.getId(), rejectionReason);
            }
        }

        boolean managerOfferIsEligible =
                eligibleOffers.stream().anyMatch(offer -> offer.getRequiredRole() != null);
        if (managerDiscountBps != null && !managerOfferIsEligible) {
            throw new BillingValidationException(
                    "Manager override requires an active rule, an authorized store manager, a reason and a percentage within the configured limit");
        }

        long[] bestLineDiscounts = new long[cartLines.size()];
        List<List<DiscountAllocationDto>> bestAllocations =
                createEmptyAllocations(cartLines.size());
        long bestTotalDiscountPaise = -1;

        for (List<OfferRuleDto> combination : buildValidCombinations(eligibleOffers)) {
            long[] lineDiscounts = new long[cartLines.size()];
            List<List<DiscountAllocationDto>> allocations =
                    createEmptyAllocations(cartLines.size());

            for (OfferRuleDto offer : combination) {
                long[] offerDiscounts =
                        calculateOfferDiscounts(
                                offer, cartLines, lineDiscounts, managerDiscountBps);
                for (int lineIndex = 0; lineIndex < cartLines.size(); lineIndex++) {
                    if (offerDiscounts[lineIndex] > 0) {
                        lineDiscounts[lineIndex] += offerDiscounts[lineIndex];
                        allocations
                                .get(lineIndex)
                                .add(
                                        new DiscountAllocationDto(
                                                offer.getId(), offerDiscounts[lineIndex]));
                    }
                }
            }

            long totalDiscountPaise = Arrays.stream(lineDiscounts).sum();
            // Keep the first equal result: priority/ID ordering makes ties deterministic.
            if (totalDiscountPaise > bestTotalDiscountPaise) {
                bestTotalDiscountPaise = totalDiscountPaise;
                bestLineDiscounts = lineDiscounts;
                bestAllocations = allocations;
            }
        }

        List<OfferDecisionDto> decisions = new ArrayList<>();
        for (OfferRuleDto offer : configuredOffers) {
            long appliedAmountPaise = 0;
            for (List<DiscountAllocationDto> lineAllocations : bestAllocations) {
                for (DiscountAllocationDto allocation : lineAllocations) {
                    if (allocation.getOfferId().equals(offer.getId())) {
                        appliedAmountPaise += allocation.getAmountPaise();
                    }
                }
            }
            String explanation =
                    appliedAmountPaise > 0
                            ? "Selected in lowest-payable valid combination"
                            : rejectionReasons.getOrDefault(
                                    offer.getId(),
                                    "Eligible; another valid combination is better or ties by priority, or no remaining value");
            decisions.add(
                    new OfferDecisionDto(
                            offer.getId(),
                            appliedAmountPaise > 0,
                            appliedAmountPaise,
                            explanation));
        }
        return new OfferEvaluationResultDto(bestLineDiscounts, bestAllocations, decisions);
    }

    private List<List<OfferRuleDto>> buildValidCombinations(List<OfferRuleDto> eligibleOffers) {
        List<List<OfferRuleDto>> combinations = new ArrayList<>();
        combinations.add(List.of());

        // For each offer, consider keeping each existing combination or adding that offer.
        // This avoids bit masks and preserves the original deterministic subset order.
        for (OfferRuleDto offer : eligibleOffers) {
            List<List<OfferRuleDto>> additionalCombinations = new ArrayList<>();
            for (List<OfferRuleDto> existingCombination : combinations) {
                boolean existingExclusiveOffer =
                        existingCombination.stream()
                                .anyMatch(existingOffer -> !existingOffer.isStackable());
                if (!existingCombination.isEmpty()
                        && (!offer.isStackable() || existingExclusiveOffer)) {
                    continue;
                }
                List<OfferRuleDto> newCombination = new ArrayList<>(existingCombination);
                newCombination.add(offer);
                additionalCombinations.add(newCombination);
            }
            combinations.addAll(additionalCombinations);
        }
        return combinations;
    }

    private String findIneligibilityReason(
            OfferRuleDto offer,
            List<CartLineDto> cartLines,
            CustomerDto customer,
            LocalDate billingDate,
            Integer managerDiscountBps,
            StaffDto manager,
            String reasonCode) {
        if (!offer.isActive()
                || billingDate.isBefore(offer.getFrom())
                || billingDate.isAfter(offer.getTo())) {
            return "Inactive or outside validity dates";
        }
        OfferConditionDto condition = offer.getCondition();
        if (condition.getCustomerTag() != null
                && !customer.getTags().contains(condition.getCustomerTag())) {
            return "Customer tag does not match";
        }
        long pairCount = cartLines.stream().map(CartLineDto::getPairRef).distinct().count();
        if (condition.getMinPairs() != null && pairCount < condition.getMinPairs()) {
            return "Not enough pairs in cart";
        }
        if (offer.getRequiredRole() != null) {
            if (managerDiscountBps == null) {
                return "Manual discount was not requested";
            }
            if (manager == null
                    || !manager.isCanOverride()
                    || !manager.getRole().equals(offer.getRequiredRole())) {
                return "Authorized store manager required";
            }
            if (offer.isReasonRequired() && (reasonCode == null || reasonCode.isBlank())) {
                return "Reason code required";
            }
            if (managerDiscountBps <= 0
                    || offer.getDiscount().getMaxBps() == null
                    || managerDiscountBps > offer.getDiscount().getMaxBps()) {
                return "Manager percentage outside allowed range";
            }
        }
        if (findEligibleLineGroups(offer, cartLines).isEmpty()) {
            return "Cart/lens conditions do not match";
        }
        return null;
    }

    private List<List<Integer>> findEligibleLineGroups(
            OfferRuleDto offer, List<CartLineDto> cartLines) {
        List<Integer> eligibleLineIndexes = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < cartLines.size(); lineIndex++) {
            ProductVariantDto variant = cartLines.get(lineIndex).getVariant();
            if (matchesLineConditions(offer, variant)) {
                eligibleLineIndexes.add(lineIndex);
            }
        }

        if ("lowest_priced_lens_line".equals(offer.getDiscount().getOn())) {
            Optional<Integer> cheapestLine =
                    eligibleLineIndexes.stream()
                            .min(
                                    Comparator.<Integer>comparingLong(
                                                    index ->
                                                            cartLines
                                                                    .get(index)
                                                                    .getVariant()
                                                                    .getPricePaise())
                                            .thenComparingInt(index -> index));
            return cheapestLine.map(index -> List.of(List.of(index))).orElse(List.of());
        }
        if ("cart".equals(offer.getScope())) {
            return eligibleLineIndexes.isEmpty() ? List.of() : List.of(eligibleLineIndexes);
        }
        if ("pair".equals(offer.getScope())) {
            Map<String, List<Integer>> pairGroups = new LinkedHashMap<>();
            for (int lineIndex : eligibleLineIndexes) {
                String pairReference = cartLines.get(lineIndex).getPairRef();
                pairGroups.computeIfAbsent(pairReference, key -> new ArrayList<>()).add(lineIndex);
            }
            List<List<Integer>> eligiblePairs = new ArrayList<>();
            for (List<Integer> pairGroup : pairGroups.values()) {
                if (!offer.getCondition().isFrameAndLens()
                        || containsFrameAndLens(pairGroup, cartLines)) {
                    eligiblePairs.add(pairGroup);
                }
            }
            return eligiblePairs;
        }

        List<List<Integer>> individualLines = new ArrayList<>();
        for (int lineIndex : eligibleLineIndexes) {
            individualLines.add(List.of(lineIndex));
        }
        return individualLines;
    }

    private boolean matchesLineConditions(OfferRuleDto offer, ProductVariantDto variant) {
        OfferConditionDto condition = offer.getCondition();
        boolean restrictedToLineType =
                "frame".equals(offer.getScope()) || "lens".equals(offer.getScope());
        if (restrictedToLineType && !offer.getScope().equals(variant.getLineType())) {
            return false;
        }
        if (condition.getLensType() != null
                && !condition.getLensType().equals(variant.getLensType())) {
            return false;
        }
        if (condition.getCoating() != null
                && !condition.getCoating().equals(variant.getCoating())) {
            return false;
        }
        return condition.getIndexMin() == null
                || variant.getIndex() != null
                        && variant.getIndex().compareTo(condition.getIndexMin()) >= 0;
    }

    private boolean containsFrameAndLens(List<Integer> lineIndexes, List<CartLineDto> cartLines) {
        boolean hasFrame = false;
        boolean hasLens = false;
        for (int lineIndex : lineIndexes) {
            String lineType = cartLines.get(lineIndex).getVariant().getLineType();
            hasFrame = hasFrame || "frame".equals(lineType);
            hasLens = hasLens || "lens".equals(lineType);
        }
        return hasFrame && hasLens;
    }

    private long[] calculateOfferDiscounts(
            OfferRuleDto offer,
            List<CartLineDto> cartLines,
            long[] existingDiscountsPaise,
            Integer managerDiscountBps) {
        long[] offerDiscountsPaise = new long[cartLines.size()];
        for (List<Integer> eligibleGroup : findEligibleLineGroups(offer, cartLines)) {
            long[] remainingPricesPaise = new long[eligibleGroup.size()];
            for (int groupIndex = 0; groupIndex < eligibleGroup.size(); groupIndex++) {
                int lineIndex = eligibleGroup.get(groupIndex);
                remainingPricesPaise[groupIndex] =
                        cartLines.get(lineIndex).getVariant().getPricePaise()
                                - existingDiscountsPaise[lineIndex];
            }

            long remainingTotalPaise = Arrays.stream(remainingPricesPaise).sum();
            long discountPaise;
            if ("flat".equals(offer.getDiscount().getType())) {
                discountPaise = Math.min(remainingTotalPaise, offer.getDiscount().getFlatPaise());
            } else {
                int discountBasisPoints =
                        offer.getDiscount().getValueBps() == null
                                ? managerDiscountBps
                                : offer.getDiscount().getValueBps();
                discountPaise =
                        MoneyUtils.multiplyAndDivideWithRounding(
                                remainingTotalPaise, discountBasisPoints, 10000);
            }

            long[] allocatedDiscounts =
                    MoneyUtils.allocateProportionally(discountPaise, remainingPricesPaise);
            for (int groupIndex = 0; groupIndex < eligibleGroup.size(); groupIndex++) {
                offerDiscountsPaise[eligibleGroup.get(groupIndex)] +=
                        allocatedDiscounts[groupIndex];
            }
        }

        long totalDiscountPaise = Arrays.stream(offerDiscountsPaise).sum();
        if (offer.getMaxDiscountPaise() != null
                && totalDiscountPaise > offer.getMaxDiscountPaise()) {
            return MoneyUtils.allocateProportionally(
                    offer.getMaxDiscountPaise(), offerDiscountsPaise);
        }
        return offerDiscountsPaise;
    }

    private List<List<DiscountAllocationDto>> createEmptyAllocations(int lineCount) {
        List<List<DiscountAllocationDto>> allocations = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            allocations.add(new ArrayList<>());
        }
        return allocations;
    }
}
