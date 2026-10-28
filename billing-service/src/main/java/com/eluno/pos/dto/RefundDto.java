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
public class RefundDto {
    private long id;

    private String creditNote;

    private OffsetDateTime createdAt;

    private List<RefundedLineDto> lines;

    private long amountPaise;
}
