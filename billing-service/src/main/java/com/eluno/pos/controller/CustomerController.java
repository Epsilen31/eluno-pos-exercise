package com.eluno.pos.controller;

import com.eluno.pos.dto.CustomerLookupResponse;
import com.eluno.pos.service.QuoteService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Validated
@RequiredArgsConstructor
public class CustomerController {
    private final QuoteService quoteService;

    @GetMapping("/lookup")
    public CustomerLookupResponse lookupCustomer(
            @RequestParam @Pattern(regexp = "\\+[0-9]{10,15}") String phone) {
        return quoteService.lookupCustomer(phone);
    }
}
