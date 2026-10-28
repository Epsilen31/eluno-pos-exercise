package com.eluno.pos.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private long id;

    private StoreDto store;

    private String invoiceNo;

    private String reference;

    private OffsetDateTime createdAt;

    private String channel;

    private String paymentMethod;

    private long grossPaise;

    private long discountPaise;

    private long taxPaise;

    private long totalPaise;

    private List<OrderLineDto> lines;

    private List<SaleTransactionDto> sales;

    private List<RefundDto> refunds;

    private boolean cancelled;
}
