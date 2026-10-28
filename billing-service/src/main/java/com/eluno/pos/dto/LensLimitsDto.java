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
public class LensLimitsDto {
    private BigDecimal sphereMin;

    private BigDecimal sphereMax;

    private BigDecimal cylinderMax;

    private boolean addRequired;

    private BigDecimal addMin;

    private BigDecimal addMax;
}
