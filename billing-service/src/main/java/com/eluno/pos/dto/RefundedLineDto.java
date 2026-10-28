package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundedLineDto {
    private long lineId;

    private int quantity;

    private long taxablePaise;

    private long taxPaise;
}
