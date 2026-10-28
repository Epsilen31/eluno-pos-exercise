package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferDecisionDto {
    private String offerId;

    private boolean applied;

    private long amountPaise;

    private String reason;
}
