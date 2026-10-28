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
public class OfferEvaluationResultDto {
    private long[] discounts;

    private List<List<DiscountAllocationDto>> allocations;

    private List<OfferDecisionDto> decisions;
}
