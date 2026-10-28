package com.eluno.pos.controller;

import com.eluno.pos.dto.OperationResponse;
import com.eluno.pos.dto.QuoteResponse;
import com.eluno.pos.dto.request.CartRequest;
import com.eluno.pos.dto.request.CheckoutRequest;
import com.eluno.pos.service.CheckoutService;
import com.eluno.pos.service.OperationService;
import com.eluno.pos.service.QuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {
    private final QuoteService quoteService;
    private final CheckoutService checkoutService;
    private final OperationService operationService;

    @PostMapping("/cart/quote")
    public QuoteResponse createQuote(@Valid @RequestBody CartRequest request) {
        return quoteService.createQuote(request);
    }

    @PostMapping("/checkouts")
    public OperationResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return operationService.toResponse(checkoutService.checkout(request));
    }
}
