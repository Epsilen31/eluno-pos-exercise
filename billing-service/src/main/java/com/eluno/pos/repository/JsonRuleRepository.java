package com.eluno.pos.repository;

import com.eluno.pos.dto.OfferConditionDto;
import com.eluno.pos.dto.OfferDiscountDto;
import com.eluno.pos.dto.OfferRuleDto;
import com.eluno.pos.dto.TaxRateDto;
import com.eluno.pos.exception.BillingValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class JsonRuleRepository {
    private final Path dataDirectory;
    private final ObjectMapper objectMapper;

    public JsonRuleRepository(
            @Value("${pos.data}") String dataDirectory, ObjectMapper objectMapper) {
        this.dataDirectory = Path.of(dataDirectory);
        this.objectMapper = objectMapper;
    }

    public List<TaxRateDto> taxRates() {
        var result = new ArrayList<TaxRateDto>();
        for (var node : readRuleFile("tax_rates.json").path("rates")) {
            int rate = node.path("rate_bps").asInt(-1);
            if (rate < 0 || rate > 10000) {
                throw new BillingValidationException("Invalid configured GST rate");
            }
            result.add(
                    new TaxRateDto(
                            node.path("hsn").asText(),
                            rate,
                            getOptionalDate(node, "effective_from"),
                            getOptionalDate(node, "effective_to")));
        }
        return result;
    }

    public List<OfferRuleDto> getOffers() {
        var result = new ArrayList<OfferRuleDto>();
        var ids = new HashSet<String>();
        for (var node : readRuleFile("offers.json")) {
            var ruleNode = node.path("rule");
            var conditionNode = ruleNode.path("condition");
            var discountNode = ruleNode.path("discount");
            var authorizationNode = ruleNode.path("requires");
            validateSupportedFields(
                    ruleNode,
                    "applies_to",
                    "condition",
                    "discount",
                    "requires",
                    "max_discount_paise");
            validateSupportedFields(
                    conditionNode,
                    "customer_tag",
                    "lens_type",
                    "index_min",
                    "coating",
                    "min_pairs_in_cart",
                    "has_frame_and_lens");
            validateSupportedFields(
                    discountNode, "type", "value_bps", "value_bps_max", "value_paise", "on");
            validateSupportedFields(authorizationNode, "role", "reason_code");
            String scope = getOptionalText(ruleNode, "applies_to");
            String type = getOptionalText(discountNode, "type");
            String id = getOptionalText(node, "offer_id");
            if (id == null || !ids.add(id)) {
                throw new BillingValidationException("Offer IDs must be unique");
            }
            if (scope == null || !Set.of("frame", "lens", "pair", "cart").contains(scope)) {
                throw new BillingValidationException("Unsupported offer scope");
            }
            if (!"percent".equals(type) && !"flat".equals(type)) {
                throw new BillingValidationException("Unsupported discount type");
            }
            if (!(getOptionalText(discountNode, "on") == null
                    || "lowest_priced_lens_line".equals(getOptionalText(discountNode, "on")))) {
                throw new BillingValidationException("Unsupported discount target");
            }
            if ("flat".equals(type)) {
                if (!(discountNode.has("value_paise")
                        && discountNode.path("value_paise").asLong() >= 0)) {
                    throw new BillingValidationException("Invalid flat discount");
                }
            } else {
                int bps =
                        discountNode
                                .path(discountNode.has("value_bps") ? "value_bps" : "value_bps_max")
                                .asInt(-1);
                if (bps < 0 || bps > 10000) {
                    throw new BillingValidationException("Invalid percent discount");
                }
                if (!(discountNode.has("value_bps") || authorizationNode.has("role"))) {
                    throw new BillingValidationException(
                            "Variable discount requires authorization rule");
                }
            }
            if (ruleNode.has("max_discount_paise")
                    && ruleNode.path("max_discount_paise").asLong() < 0) {
                throw new BillingValidationException("Negative discount cap");
            }
            var from = getOptionalDate(node, "valid_from");
            var to = getOptionalDate(node, "valid_to");
            if (from == null || to == null || to.isBefore(from)) {
                throw new BillingValidationException("Invalid offer dates");
            }
            result.add(
                    new OfferRuleDto(
                            id,
                            node.path("active").asBoolean(),
                            from,
                            to,
                            node.path("stackable").asBoolean(),
                            node.path("priority").asInt(),
                            scope,
                            new OfferConditionDto(
                                    getOptionalText(conditionNode, "customer_tag"),
                                    getOptionalText(conditionNode, "lens_type"),
                                    conditionNode.has("index_min")
                                            ? conditionNode.get("index_min").decimalValue()
                                            : null,
                                    getOptionalText(conditionNode, "coating"),
                                    getOptionalInteger(conditionNode, "min_pairs_in_cart"),
                                    conditionNode.path("has_frame_and_lens").asBoolean()),
                            new OfferDiscountDto(
                                    type,
                                    getOptionalInteger(discountNode, "value_bps"),
                                    getOptionalInteger(discountNode, "value_bps_max"),
                                    discountNode.has("value_paise")
                                            ? discountNode.get("value_paise").longValue()
                                            : null,
                                    getOptionalText(discountNode, "on")),
                            ruleNode.has("max_discount_paise")
                                    ? ruleNode.get("max_discount_paise").longValue()
                                    : null,
                            getOptionalText(authorizationNode, "role"),
                            authorizationNode.path("reason_code").asBoolean()));
        }
        return result;
    }

    private JsonNode readRuleFile(String name) {
        try {
            return objectMapper.readTree(dataDirectory.resolve(name).toFile());
        } catch (Exception e) {
            throw new BillingValidationException(
                    "Cannot load rule file " + name + ": " + e.getMessage());
        }
    }

    private static void validateSupportedFields(JsonNode node, String... allowed) {
        var set = Set.of(allowed);
        node.fieldNames()
                .forEachRemaining(
                        fieldName -> {
                            if (!set.contains(fieldName)) {
                                throw new BillingValidationException(
                                        "Unsupported rule field: " + fieldName);
                            }
                        });
    }

    private static String getOptionalText(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    private static Integer getOptionalInteger(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? node.get(fieldName).asInt() : null;
    }

    private static LocalDate getOptionalDate(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? LocalDate.parse(node.get(fieldName).asText()) : null;
    }
}
