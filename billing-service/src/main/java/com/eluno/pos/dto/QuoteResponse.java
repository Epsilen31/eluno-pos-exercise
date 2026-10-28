package com.eluno.pos.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {
    private StoreDto store;

    private CustomerDto customer;

    private PrescriptionDto prescription;

    private LocalDate date;

    private String staffId;

    private String channel;

    private String paymentMethod;

    private String managerId;

    private String reasonCode;

    private List<PricedCartLineDto> lines;

    private List<OfferDecisionDto> offers;

    private List<String> warnings;

    private long grossPaise;

    private long discountPaise;

    private long taxablePaise;

    private long cgstPaise;

    private long sgstPaise;

    private long totalPaise;
}
