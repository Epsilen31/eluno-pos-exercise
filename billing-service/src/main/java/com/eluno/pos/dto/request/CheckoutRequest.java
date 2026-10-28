package com.eluno.pos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CheckoutRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,80}")
    private String reference;

    @NotNull @Valid private CartRequest cart;

    @Positive private long expectedTotalPaise;
}
