package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Saved checkout/refund progress returned by the API, including a typed calculation snapshot. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {
    private String reference;

    private String kind;

    private String invoiceNo;

    private String state;

    private String externalId;

    private Long orderId;

    private String message;

    private String updatedAt;

    private Object snapshot;
}
