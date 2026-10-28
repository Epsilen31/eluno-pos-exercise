package com.eluno.pos.controller;

import com.eluno.pos.dto.OperationResponse;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.request.RefundRequest;
import com.eluno.pos.service.CheckoutService;
import com.eluno.pos.service.InvoiceService;
import com.eluno.pos.service.OperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final InvoiceService invoiceService;
    private final CheckoutService checkoutService;
    private final OperationService operationService;

    @GetMapping("/{id}")
    public OrderDto getOrder(@PathVariable long id) {
        return invoiceService.getOrder(id);
    }

    @GetMapping(value = "/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public String getInvoice(@PathVariable long id) {
        return invoiceService.generateInvoiceHtml(id);
    }

    @PostMapping("/{id}/refund")
    public OperationResponse refundOrder(
            @PathVariable long id, @Valid @RequestBody RefundRequest request) {
        return operationService.toResponse(checkoutService.refund(id, request));
    }
}
