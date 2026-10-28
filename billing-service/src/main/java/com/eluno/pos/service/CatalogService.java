package com.eluno.pos.service;

import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.client.StoreToolClient;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.dto.StoreDto;
import com.eluno.pos.exception.BillingValidationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final ShopifyClient shopifyClient;
    private final StoreToolClient storeToolClient;
    private final QuoteService quoteService;

    public List<StoreDto> getStores() {
        return shopifyClient.getStores();
    }

    public List<StaffDto> getStoreStaff(String storeCode) {
        StoreDto store = quoteService.findStoreByCode(storeCode);
        return storeToolClient.getStaffByStore(store);
    }

    public List<ProductVariantDto> getCatalog() {
        return shopifyClient.getCatalog();
    }

    public int getFrameStock(String storeCode, long variantId) {
        ProductVariantDto frame =
                getCatalog().stream()
                        .filter(variant -> variant.getId() == variantId)
                        .filter(variant -> "frame".equals(variant.getLineType()))
                        .findFirst()
                        .orElseThrow(() -> new BillingValidationException("Unknown frame"));
        return shopifyClient.getAvailableStock(frame, quoteService.findStoreByCode(storeCode));
    }
}
