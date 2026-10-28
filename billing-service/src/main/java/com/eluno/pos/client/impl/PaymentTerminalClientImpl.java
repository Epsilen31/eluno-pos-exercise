package com.eluno.pos.client.impl;

import com.eluno.pos.client.ExternalApiClient;
import com.eluno.pos.client.PaymentTerminalClient;
import com.eluno.pos.dto.PaymentChargeDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentTerminalClientImpl implements PaymentTerminalClient {
    private final ExternalApiClient externalApiClient;

    @Override
    public PaymentChargeDto chargePayment(long amountPaise, String method, String reference) {
        return parseChargeResponse(
                externalApiClient.post(
                        "/terminal/charges",
                        Map.of(
                                "amount_paise",
                                amountPaise,
                                "method",
                                method,
                                "order_ref",
                                reference)));
    }

    @Override
    public PaymentChargeDto getChargeStatus(String id) {
        return parseChargeResponse(externalApiClient.get("/terminal/charges/" + id));
    }

    @Override
    public String refundPayment(String id, long amountPaise, String reason) {
        return externalApiClient
                .post(
                        "/terminal/refunds",
                        Map.of("charge_id", id, "amount_paise", amountPaise, "reason", reason))
                .path("refund_id")
                .asText();
    }

    private PaymentChargeDto parseChargeResponse(JsonNode node) {
        return new PaymentChargeDto(
                node.path("charge_id").asText(),
                node.path("status").asText(),
                node.path("amount_paise").asLong(),
                node.path("refunded_paise").asLong(),
                node.path("order_ref").asText());
    }
}
