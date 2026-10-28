package com.eluno.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxBreakdownDto {
    private long taxablePaise;

    private long cgstPaise;

    private long sgstPaise;

    private int rateBps;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public long getTotalPaise() {
        return cgstPaise + sgstPaise;
    }
}
