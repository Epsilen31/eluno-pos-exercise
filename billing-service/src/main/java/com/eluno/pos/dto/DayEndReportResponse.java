package com.eluno.pos.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayEndReportResponse {
    private String storeCode;

    private LocalDate date;

    private String timezone;

    private long billCount;

    private long grossPaise;

    private long discountsPaise;

    private long salesPaise;

    private long taxablePaise;

    private long taxPaise;

    private long refundsPaise;

    private long refundedTaxablePaise;

    private long refundedTaxPaise;

    private long netPaise;

    private long netTaxablePaise;

    private long netTaxPaise;

    private long averageBillPaise;

    private Map<String, ReportSplitDto> paymentMethods;

    private Map<String, ReportSplitDto> channels;

    private long unresolvedOperations;

    private List<String> reconciliationIssues;
}
