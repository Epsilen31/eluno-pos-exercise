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
public class EyePrescriptionDto {
    private BigDecimal sphere;

    private BigDecimal cylinder;

    private Integer axis;

    private BigDecimal add;
}
