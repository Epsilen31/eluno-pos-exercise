package com.eluno.pos.client;

import com.eluno.pos.dto.PaymentChargeDto;

public interface PaymentTerminalClient {
    PaymentChargeDto chargePayment(long amountPaise, String method, String reference);

    PaymentChargeDto getChargeStatus(String chargeId);

    String refundPayment(String chargeId, long amountPaise, String reason);
}
