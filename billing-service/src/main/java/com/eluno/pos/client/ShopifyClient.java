package com.eluno.pos.client;

import com.eluno.pos.dto.CustomerDto;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.QuoteResponse;
import com.eluno.pos.dto.RefundDto;
import com.eluno.pos.dto.RefundPlanDto;
import com.eluno.pos.dto.StoreDto;
import java.util.List;

public interface ShopifyClient {
    CustomerDto findCustomerByPhone(String phone);

    List<StoreDto> getStores();

    List<ProductVariantDto> getCatalog();

    int getAvailableStock(ProductVariantDto frame, StoreDto store);

    OrderDto createOrder(QuoteResponse quote, String reference, String invoice, String chargeId);

    OrderDto getOrder(long id);

    List<OrderDto> getOrders(StoreDto store);

    RefundDto createRefund(RefundPlanDto plan, String terminalRefundId);
}
