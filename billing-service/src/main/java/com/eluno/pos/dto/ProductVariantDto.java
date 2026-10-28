package com.eluno.pos.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private long id;

    private String sku;

    private String title;

    private String lineType;

    private String hsn;

    private long pricePaise;

    private Long inventoryItemId;

    private boolean rxAble;

    private String lensType;

    private BigDecimal index;

    private String coating;

    private LensLimitsDto limits;
}
