package com.eluno.pos.controller;

import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.dto.StoreDto;
import com.eluno.pos.service.CatalogService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;

    @GetMapping("/stores")
    public List<StoreDto> getStores() {
        return catalogService.getStores();
    }

    @GetMapping("/stores/{code}/staff")
    public List<StaffDto> getStoreStaff(@PathVariable String code) {
        return catalogService.getStoreStaff(code);
    }

    @GetMapping("/catalog")
    public List<ProductVariantDto> getCatalog() {
        return catalogService.getCatalog();
    }

    @GetMapping("/stock")
    public Map<String, Integer> getFrameStock(
            @RequestParam String storeCode, @RequestParam long variantId) {
        return Map.of("available", catalogService.getFrameStock(storeCode, variantId));
    }
}
