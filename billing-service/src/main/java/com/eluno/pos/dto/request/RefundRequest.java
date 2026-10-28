package com.eluno.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,80}")
    private String reference;

    @NotBlank
    @Size(max = 30)
    private String staffId;

    @NotBlank
    @Size(max = 100)
    private String reasonCode;
}
