package com.eluno.pos.dto;

import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDto {
    private String id;

    private long customerId;

    private LocalDate date;

    private LocalDate validUntil;

    private String source;

    private EyePrescriptionDto right;

    private EyePrescriptionDto left;

    private Map<String, Object> pd;
}
