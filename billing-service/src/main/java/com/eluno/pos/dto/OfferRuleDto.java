package com.eluno.pos.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferRuleDto {
    private String id;

    private boolean active;

    private LocalDate from;

    private LocalDate to;

    private boolean stackable;

    private int priority;

    private String scope;

    private OfferConditionDto condition;

    private OfferDiscountDto discount;

    private Long maxDiscountPaise;

    private String requiredRole;

    private boolean reasonRequired;
}
