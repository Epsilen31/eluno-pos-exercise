package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPlanDto {
    private OrderDto order;

    private String creditNote;

    private String reason;

    private String staffId;

    private long amountPaise;

    private long taxablePaise;

    private long cgstPaise;

    private long sgstPaise;

    private SaleTransactionDto sale;
}
