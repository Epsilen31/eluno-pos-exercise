package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferDiscountDto {
    private String type;

    private Integer valueBps;

    private Integer maxBps;

    private Long flatPaise;

    private String on;
}
