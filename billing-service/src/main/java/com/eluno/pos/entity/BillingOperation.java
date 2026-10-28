package com.eluno.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable checkout/refund state managed by Hibernate. */
@Entity
@Table(name = "operations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingOperation {
    @Id
    @Column(length = 100, nullable = false)
    private String reference;

    @Column(length = 20, nullable = false)
    private String kind;

    @Lob
    @Column(name = "request_json", nullable = false)
    private String requestJson;

    @Column(name = "invoice_no", length = 150, nullable = false, unique = true)
    private String invoiceNo;

    @Column(length = 60, nullable = false)
    private String state;

    @Lob
    @Column(nullable = false)
    private String snapshot;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 2000)
    private String message;

    @Column(name = "updated_at", length = 50, nullable = false)
    private String updatedAt;
}
