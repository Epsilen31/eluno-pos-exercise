package com.eluno.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartPairRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,30}")
    private String pairRef;

    @Positive private Long frameVariantId;

    @Positive private Long lensVariantId;
}
