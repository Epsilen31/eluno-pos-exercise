package com.eluno.pos.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricedCartLineDto {
    private CartLineDto item;

    private long discountPaise;

    private TaxBreakdownDto tax;

    private List<DiscountAllocationDto> allocations;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public long getTotalPaise() {
        return item.getVariant().getPricePaise() - discountPaise;
    }
}
