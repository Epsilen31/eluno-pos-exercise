package com.eluno.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSeries {
    @Id
    @Column(length = 100, nullable = false)
    private String prefix;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;
}
