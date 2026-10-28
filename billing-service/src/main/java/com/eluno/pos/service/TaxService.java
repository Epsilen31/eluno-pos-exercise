package com.eluno.pos.service;

import com.eluno.pos.dto.TaxBreakdownDto;
import com.eluno.pos.dto.TaxRateDto;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.util.MoneyUtils;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaxService {
    public TaxBreakdownDto calculate(
            long inclusivePricePaise,
            String hsn,
            LocalDate billingDate,
            List<TaxRateDto> taxRates) {
        List<TaxRateDto> matchingRates = taxRates.stream()
                .filter(rate -> rate.getHsn().equals(hsn))
                .filter(rate -> !billingDate.isBefore(rate.getFrom()))
                .filter(rate -> rate.getTo() == null || !billingDate.isAfter(rate.getTo()))
                .toList();

        if (matchingRates.size() != 1) {
            throw new BillingValidationException(
                    "Expected one effective GST rate for HSN " + hsn + " on " + billingDate);
        }
        return calculateIncludedTax(inclusivePricePaise, matchingRates.getFirst().getRateBps());
    }

    public TaxBreakdownDto calculateIncludedTax(long inclusivePricePaise, int rateBasisPoints) {
        if (rateBasisPoints < 0) {
            throw new BillingValidationException("GST rate cannot be negative");
        }

        long taxablePaise = MoneyUtils.multiplyAndDivideWithRounding(
                inclusivePricePaise, 10000, 10000L + rateBasisPoints);
        long totalTaxPaise = inclusivePricePaise - taxablePaise;
        long cgstPaise = MoneyUtils.multiplyAndDivideWithRounding(totalTaxPaise, 1, 2);

        long sgstPaise = totalTaxPaise - cgstPaise;
        return new TaxBreakdownDto(taxablePaise, cgstPaise, sgstPaise, rateBasisPoints);
    }
}
