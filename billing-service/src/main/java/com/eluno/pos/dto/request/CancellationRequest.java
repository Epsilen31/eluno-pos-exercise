package com.eluno.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationRequest {
    @NotBlank
    @Size(max = 30)
    private String staffId;

    @NotBlank
    @Size(max = 100)
    private String reasonCode;
}
