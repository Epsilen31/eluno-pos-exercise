package com.eluno.pos.service;

import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.dto.DayEndReportResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportingService {
    private final ShopifyClient shopify;
    private final QuoteService quotes;
    private final OperationPersistenceService operations;
    private final DayEndReportService dayEndReportService;

    public DayEndReportResponse dayEnd(String code, LocalDate date) {
        var store = quotes.findStoreByCode(code);
        return dayEndReportService.calculate(
                store, date, shopify.getOrders(store), operations.unresolved(code));
    }
}
