package com.eluno.pos.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferConditionDto {
    private String customerTag;

    private String lensType;

    private BigDecimal indexMin;

    private String coating;

    private Integer minPairs;

    private boolean frameAndLens;
}
