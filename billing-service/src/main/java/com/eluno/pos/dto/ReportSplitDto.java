package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSplitDto {
    private long salesPaise;

    private long refundsPaise;

    private long netPaise;
}
