package com.eluno.pos.service;

import com.eluno.pos.dto.OperationResponse;
import com.eluno.pos.entity.BillingOperation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationService {
    private final OperationPersistenceService operationPersistenceService;

    public List<Map<String, Object>> getInvoiceLedger() {
        return operationPersistenceService.ledger();
    }

    public List<Map<String, Object>> getAuditEvents(String reference) {
        return operationPersistenceService.audit(reference);
    }

    public OperationResponse toResponse(BillingOperation operation) {
        Object calculationSnapshot =
                operationPersistenceService.deserializeJson(operation.getSnapshot(), Object.class);
        return new OperationResponse(
                operation.getReference(),
                operation.getKind(),
                operation.getInvoiceNo(),
                operation.getState(),
                operation.getExternalId(),
                operation.getOrderId(),
                operation.getMessage(),
                operation.getUpdatedAt(),
                calculationSnapshot);
    }
}
