package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleTransactionDto {
    private long id;

    private String gateway;

    private String authorization;

    private long amountPaise;
}
