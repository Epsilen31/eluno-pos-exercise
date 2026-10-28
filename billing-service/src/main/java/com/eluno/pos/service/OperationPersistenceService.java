package com.eluno.pos.service;

import com.eluno.pos.dto.RefundPlanDto;
import com.eluno.pos.entity.AuditEvent;
import com.eluno.pos.entity.BillingOperation;
import com.eluno.pos.entity.InvoiceSeries;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.repository.AuditEventRepository;
import com.eluno.pos.repository.BillingOperationRepository;
import com.eluno.pos.repository.InvoiceSeriesRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short local JPA transactions. Checkout performs remote calls outside these transactions. */
@Service
@RequiredArgsConstructor
public class OperationPersistenceService {
    private final BillingOperationRepository billingOperationRepository;
    private final InvoiceSeriesRepository invoiceSeriesRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize operation", exception);
        }
    }

    public <T> T deserializeJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read persisted operation", exception);
        }
    }

    @Transactional(readOnly = true)
    public BillingOperation find(String reference) {
        return billingOperationRepository.findById(reference).orElse(null);
    }

    @Transactional
    public BillingOperation reserve(
            String reference,
            String kind,
            Object request,
            String prefix,
            long lastShopifyNumber,
            Object snapshot,
            String actor) {
        if (billingOperationRepository.existsById(reference)) {
            throw new BillingValidationException("Operation reference is already reserved");
        }
        // The existing series stays locked until the number, operation and audit are committed.
        // CheckoutService serializes creation of new series in this single-process exercise.
        InvoiceSeries series =
                invoiceSeriesRepository
                        .findByPrefixForUpdate(prefix)
                        .orElseGet(() -> new InvoiceSeries(prefix, 1));
        long invoiceNumber = Math.max(lastShopifyNumber + 1, series.getNextNumber());
        series.setNextNumber(invoiceNumber + 1);
        invoiceSeriesRepository.save(series);

        String invoiceNo = prefix + String.format("%05d", invoiceNumber);
        Long orderId =
                snapshot instanceof RefundPlanDto refundPlan ? refundPlan.getOrder().getId() : null;
        BillingOperation operation =
                BillingOperation.builder()
                        .reference(reference)
                        .kind(kind)
                        .requestJson(serializeJson(request))
                        .invoiceNo(invoiceNo)
                        .state("READY")
                        .snapshot(serializeJson(snapshot))
                        .orderId(orderId)
                        .updatedAt(now())
                        .build();
        billingOperationRepository.saveAndFlush(operation);
        saveAuditEvent(reference, actor, "RESERVED", invoiceNo);
        return operation;
    }

    @Transactional
    public BillingOperation update(
            BillingOperation operation,
            String state,
            String externalId,
            Long orderId,
            String message,
            String actor) {
        BillingOperation savedOperation =
                billingOperationRepository
                        .findById(operation.getReference())
                        .orElseThrow(
                                () ->
                                        new BillingValidationException(
                                                "Unknown operation reference"));
        savedOperation.setState(state);
        savedOperation.setExternalId(externalId);
        savedOperation.setOrderId(orderId);
        savedOperation.setMessage(message);
        savedOperation.setUpdatedAt(now());
        billingOperationRepository.saveAndFlush(savedOperation);
        saveAuditEvent(savedOperation.getReference(), actor, state, message);
        return savedOperation;
    }

    @Transactional
    public void audit(String reference, String actor, String action, String reason) {
        saveAuditEvent(reference, actor, action, reason);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ledger() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BillingOperation operation :
                billingOperationRepository.findAllByOrderByUpdatedAtAsc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("REFERENCE", operation.getReference());
            row.put("KIND", operation.getKind());
            row.put("INVOICE_NO", operation.getInvoiceNo());
            row.put("STATE", operation.getState());
            row.put("ORDER_ID", operation.getOrderId());
            row.put("MESSAGE", operation.getMessage());
            row.put("UPDATED_AT", operation.getUpdatedAt());
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(String reference) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuditEvent event : auditEventRepository.findByReferenceOrderByIdAsc(reference)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ACTOR", event.getActor());
            row.put("ACTION", event.getAction());
            row.put("REASON", event.getReason());
            row.put("OCCURRED_AT", event.getOccurredAt());
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long unresolved(String storeCode) {
        return billingOperationRepository.countUnresolvedByInvoicePrefix("ELU/" + storeCode + "/%");
    }

    private void saveAuditEvent(String reference, String actor, String action, String reason) {
        auditEventRepository.save(
                AuditEvent.builder()
                        .reference(reference)
                        .actor(actor)
                        .action(action)
                        .reason(reason)
                        .occurredAt(now())
                        .build());
    }

    private String now() {
        return OffsetDateTime.now(clock).toString();
    }
}
