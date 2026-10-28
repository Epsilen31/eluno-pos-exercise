package com.eluno.pos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartRequest {
    @NotBlank
    @Pattern(regexp = "\\+[0-9]{10,15}")
    private String phone;

    @NotBlank
    @Size(max = 20)
    private String storeCode;

    @NotBlank
    @Size(max = 30)
    private String staffId;

    @NotBlank
    @Pattern(regexp = "walk_in|whatsapp_booked|online_pickup")
    private String channel;

    @NotBlank
    @Pattern(regexp = "upi|card|cash")
    private String paymentMethod;

    @NotEmpty
    @Size(max = 10)
    private List<@Valid CartPairRequest> pairs;

    @Min(1)
    @Max(1000)
    private Integer managerDiscountBps;

    @Size(max = 30)
    private String managerId;

    @Size(max = 100)
    private String reasonCode;
}
