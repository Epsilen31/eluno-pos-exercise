package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentChargeDto {
    private String id;

    private String status;

    private long amountPaise;

    private long refundedPaise;

    private String orderReference;
}
