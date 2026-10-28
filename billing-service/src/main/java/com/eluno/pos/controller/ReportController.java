package com.eluno.pos.controller;

import com.eluno.pos.dto.DayEndReportResponse;
import com.eluno.pos.service.ReportingService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportingService reportingService;

    @GetMapping("/day-end")
    public DayEndReportResponse getDayEndReport(
            @RequestParam String storeCode, @RequestParam LocalDate date) {
        return reportingService.dayEnd(storeCode, date);
    }
}
