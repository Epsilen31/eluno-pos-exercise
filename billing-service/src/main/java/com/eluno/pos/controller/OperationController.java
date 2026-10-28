package com.eluno.pos.controller;

import com.eluno.pos.dto.OperationResponse;
import com.eluno.pos.dto.request.CancellationRequest;
import com.eluno.pos.dto.request.PaymentEvidenceRequest;
import com.eluno.pos.service.CheckoutService;
import com.eluno.pos.service.OperationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationController {
    private final CheckoutService checkoutService;
    private final OperationService operationService;

    @GetMapping
    public List<Map<String, Object>> getOperations() {
        return operationService.getInvoiceLedger();
    }

    @GetMapping("/{reference}")
    public OperationResponse getOperation(@PathVariable String reference) {
        return operationService.toResponse(checkoutService.get(reference));
    }

    @PostMapping("/{reference}/resume")
    public OperationResponse resumeOperation(@PathVariable String reference) {
        return operationService.toResponse(checkoutService.resume(reference));
    }

    @PostMapping("/{reference}/reconcile-payment")
    public OperationResponse reconcilePayment(
            @PathVariable String reference, @Valid @RequestBody PaymentEvidenceRequest request) {
        return operationService.toResponse(
                checkoutService.reconcilePayment(
                        reference,
                        request.getChargeId(),
                        request.getStaffId(),
                        request.getReasonCode()));
    }

    @PostMapping("/{reference}/cancel")
    public OperationResponse cancelOperation(
            @PathVariable String reference, @Valid @RequestBody CancellationRequest request) {
        return operationService.toResponse(
                checkoutService.cancel(reference, request.getStaffId(), request.getReasonCode()));
    }

    @GetMapping("/{reference}/audit")
    public List<Map<String, Object>> getAuditEvents(@PathVariable String reference) {
        checkoutService.get(reference);
        return operationService.getAuditEvents(reference);
    }
}
