package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineDto {
    private long id;

    private long variantId;

    private String title;

    private String lineType;

    private String hsn;

    private String prescriptionId;

    private int quantity;

    private long unitPricePaise;

    private long discountPaise;

    private long cgstPaise;

    private long sgstPaise;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public long getTotalPaise() {
        return Math.multiplyExact(unitPricePaise, quantity) - discountPaise;
    }
}
