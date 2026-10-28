package com.eluno.pos.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRateDto {
    private String hsn;

    private int rateBps;

    private LocalDate from;

    private LocalDate to;
}
