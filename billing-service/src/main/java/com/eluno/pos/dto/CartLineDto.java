package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartLineDto {
    private ProductVariantDto variant;

    private String pairRef;

    private PrescriptionDto prescription;
}
