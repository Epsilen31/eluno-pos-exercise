package com.eluno.pos.service;

import com.eluno.pos.client.PaymentTerminalClient;
import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.PaymentChargeDto;
import com.eluno.pos.dto.QuoteResponse;
import com.eluno.pos.dto.RefundDto;
import com.eluno.pos.dto.RefundPlanDto;
import com.eluno.pos.dto.request.CheckoutRequest;
import com.eluno.pos.dto.request.RefundRequest;
import com.eluno.pos.entity.BillingOperation;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.exception.UpstreamApiException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Single till process. H2's file lock prevents a second process using this ledger. */
@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final ShopifyClient shopifyClient;
    private final PaymentTerminalClient paymentTerminalClient;
    private final QuoteService quoteService;
    private final OperationPersistenceService operationPersistenceService;
    private final RefundService refundService;

    public synchronized BillingOperation checkout(CheckoutRequest request) {
        String reference = "sale-" + request.getReference();
        BillingOperation operation = operationPersistenceService.find(reference);
        if (operation != null) {
            if (!operation
                    .getRequestJson()
                    .equals(operationPersistenceService.serializeJson(request))) {
                throw new BillingValidationException(
                        "Reference already belongs to a different checkout payload");
            }
        } else {
            QuoteResponse quote = quoteService.createQuote(request.getCart());
            if (request.getExpectedTotalPaise() != quote.getTotalPaise()) {
                throw new BillingValidationException(
                        "Price changed or total not accepted; request a fresh quote before charging");
            }
            List<OrderDto> orders = shopifyClient.getOrders(quote.getStore());
            if (!(orders.stream().noneMatch(o -> o.getReference().equals(reference)))) {
                throw new BillingValidationException(
                        "Checkout reference already exists in Shopify but is missing locally; reconcile the ledger before proceeding");
            }
            String prefix = quote.getStore().getInvoicePrefix();
            operation =
                    operationPersistenceService.reserve(
                            reference,
                            "SALE",
                            request,
                            prefix,
                            findLastInvoiceNumber(
                                    orders.stream().map(OrderDto::getInvoiceNo).toList(), prefix),
                            quote,
                            quote.getStaffId());
        }
        return continueCheckout(operation);
    }

    private BillingOperation continueCheckout(BillingOperation operation) {
        QuoteResponse quote =
                operationPersistenceService.deserializeJson(
                        operation.getSnapshot(), QuoteResponse.class);
        if (operation.getState().equals("COMPLETED") || operation.getState().equals("CANCELLED"))
            return operation;
        if (operation.getState().equals("ORDER_PENDING")
                || operation.getState().equals("ORDER_RECONCILIATION_REQUIRED")) {
            String reference = operation.getReference();
            List<OrderDto> matches =
                    shopifyClient.getOrders(quote.getStore()).stream()
                            .filter(o -> o.getReference().equals(reference))
                            .toList();
            if (matches.size() == 1)
                return operationPersistenceService.update(
                        operation,
                        "COMPLETED",
                        operation.getExternalId(),
                        matches.getFirst().getId(),
                        "Recovered Shopify order by stable checkout reference",
                        quote.getStaffId());
            return operationPersistenceService.update(
                    operation,
                    "ORDER_RECONCILIATION_REQUIRED",
                    operation.getExternalId(),
                    null,
                    "Order outcome unknown. No new create sent. Verify upstream before resolving this bill.",
                    quote.getStaffId());
        }
        if (operation.getState().equals("PAYMENT_PENDING")
                || operation.getState().equals("PAYMENT_RECONCILIATION_REQUIRED"))
            return operationPersistenceService.update(
                    operation,
                    "PAYMENT_RECONCILIATION_REQUIRED",
                    operation.getExternalId(),
                    null,
                    "Payment outcome unknown. Do not charge again; inspect terminal with operator.",
                    quote.getStaffId());
        if (operation.getState().equals("READY")) {
            // Revalidate inventory, prescription and price before first payment, including after a
            // restart.
            CheckoutRequest originalRequest =
                    operationPersistenceService.deserializeJson(
                            operation.getRequestJson(), CheckoutRequest.class);
            QuoteResponse freshQuote = quoteService.createQuote(originalRequest.getCart());
            if (!freshQuote.equals(quote)) {
                throw new BillingValidationException(
                        "Quote changed since invoice reservation; cancel this uncharged operation and quote again");
            }
            operation =
                    operationPersistenceService.update(
                            operation,
                            "PAYMENT_PENDING",
                            null,
                            null,
                            "Payment intent persisted before terminal call",
                            quote.getStaffId());
            try {
                PaymentChargeDto charge =
                        paymentTerminalClient.chargePayment(
                                quote.getTotalPaise(),
                                quote.getPaymentMethod(),
                                operation.getReference());
                if (!("captured".equals(charge.getStatus())
                        && charge.getAmountPaise() == quote.getTotalPaise()
                        && charge.getOrderReference().equals(operation.getReference()))) {
                    throw new BillingValidationException(
                            "Terminal response does not match expected captured payment");
                }
                operation =
                        operationPersistenceService.update(
                                operation,
                                "PAID",
                                charge.getId(),
                                null,
                                "Payment captured",
                                quote.getStaffId());
            } catch (UpstreamApiException exception) {
                return operationPersistenceService.update(
                        operation,
                        exception.isOutcomeUncertain()
                                ? "PAYMENT_RECONCILIATION_REQUIRED"
                                : "READY",
                        null,
                        null,
                        exception.getMessage(),
                        quote.getStaffId());
            }
        }
        if (operation.getState().equals("PAID")) {
            operation =
                    operationPersistenceService.update(
                            operation,
                            "ORDER_PENDING",
                            operation.getExternalId(),
                            null,
                            "Shopify write intent persisted",
                            quote.getStaffId());
            try {
                OrderDto order =
                        shopifyClient.createOrder(
                                quote,
                                operation.getReference(),
                                operation.getInvoiceNo(),
                                operation.getExternalId());
                return operationPersistenceService.update(
                        operation,
                        "COMPLETED",
                        operation.getExternalId(),
                        order.getId(),
                        "Invoice confirmed in Shopify",
                        quote.getStaffId());
            } catch (UpstreamApiException exception) {
                return operationPersistenceService.update(
                        operation,
                        exception.isOutcomeUncertain() ? "ORDER_RECONCILIATION_REQUIRED" : "PAID",
                        operation.getExternalId(),
                        null,
                        exception.getMessage(),
                        quote.getStaffId());
            }
        }
        return operation;
    }

    public synchronized BillingOperation refund(long orderId, RefundRequest request) {
        String reference = "refund-" + request.getReference();
        String requestJson =
                operationPersistenceService.serializeJson(
                        Map.of("orderId", orderId, "request", request));
        BillingOperation operation = operationPersistenceService.find(reference);
        if (operation != null) {
            if (!operation.getRequestJson().equals(requestJson)) {
                throw new BillingValidationException(
                        "Reference already belongs to a different refund payload");
            }
        } else {
            OrderDto order = shopifyClient.getOrder(orderId);
            quoteService.findStaffAtStore(order.getStore(), request.getStaffId());
            // One full-refund operation per order, including pending/uncertain operations.
            if (!(operationPersistenceService.ledger().stream()
                    .noneMatch(
                            row ->
                                    "REFUND".equals(row.get("KIND"))
                                            && Objects.equals(row.get("ORDER_ID"), orderId)))) {
                throw new BillingValidationException(
                        "A refund operation already exists for this order; resume it with its original reference");
            }
            RefundPlanDto refundPlan =
                    refundService.calculateFullRefund(
                            order, "pending", request.getReasonCode(), request.getStaffId());
            // Verify payment belongs to the connected terminal before reserving the credit note.
            PaymentChargeDto charge =
                    paymentTerminalClient.getChargeStatus(refundPlan.getSale().getAuthorization());
            if (!(charge.getAmountPaise() == refundPlan.getAmountPaise()
                    && charge.getRefundedPaise() == 0)) {
                throw new BillingValidationException(
                        "Terminal payment does not match or is already refunded; reconcile first");
            }
            String prefix = order.getStore().getInvoicePrefix() + "CN/";
            List<String> existing =
                    shopifyClient.getOrders(order.getStore()).stream()
                            .flatMap(o -> o.getRefunds().stream())
                            .map(RefundDto::getCreditNote)
                            .toList();
            operation =
                    operationPersistenceService.reserve(
                            reference,
                            "REFUND",
                            Map.of("orderId", orderId, "request", request),
                            prefix,
                            findLastInvoiceNumber(existing, prefix),
                            refundPlan,
                            request.getStaffId());
            operation =
                    operationPersistenceService.update(
                            operation,
                            "READY",
                            null,
                            orderId,
                            "Credit note reserved; original invoice " + order.getInvoiceNo(),
                            request.getStaffId());
        }
        return continueRefund(operation);
    }

    private BillingOperation continueRefund(BillingOperation operation) {
        RefundPlanDto savedPlan =
                operationPersistenceService.deserializeJson(
                        operation.getSnapshot(), RefundPlanDto.class);
        RefundPlanDto refundPlan =
                new RefundPlanDto(
                        savedPlan.getOrder(),
                        operation.getInvoiceNo(),
                        savedPlan.getReason(),
                        savedPlan.getStaffId(),
                        savedPlan.getAmountPaise(),
                        savedPlan.getTaxablePaise(),
                        savedPlan.getCgstPaise(),
                        savedPlan.getSgstPaise(),
                        savedPlan.getSale());
        if (operation.getState().equals("COMPLETED")) return operation;
        if (operation.getState().equals("REFUND_ORDER_PENDING")
                || operation.getState().equals("REFUND_ORDER_RECONCILIATION_REQUIRED")) {
            String creditNote = operation.getInvoiceNo();
            List<RefundDto> found =
                    shopifyClient.getOrder(refundPlan.getOrder().getId()).getRefunds().stream()
                            .filter(r -> r.getCreditNote().equals(creditNote))
                            .toList();
            if (found.size() == 1)
                return operationPersistenceService.update(
                        operation,
                        "COMPLETED",
                        operation.getExternalId(),
                        refundPlan.getOrder().getId(),
                        "Recovered credit note in Shopify",
                        refundPlan.getStaffId());
            return operationPersistenceService.update(
                    operation,
                    "REFUND_ORDER_RECONCILIATION_REQUIRED",
                    operation.getExternalId(),
                    refundPlan.getOrder().getId(),
                    "Refund outcome unknown; no repeated restock or refund write sent",
                    refundPlan.getStaffId());
        }
        if (operation.getState().equals("REFUND_PAYMENT_PENDING")
                || operation.getState().equals("REFUND_PAYMENT_RECONCILIATION_REQUIRED"))
            return operationPersistenceService.update(
                    operation,
                    "REFUND_PAYMENT_RECONCILIATION_REQUIRED",
                    operation.getExternalId(),
                    refundPlan.getOrder().getId(),
                    "Terminal refund outcome unknown; operator reconciliation required",
                    refundPlan.getStaffId());
        if (operation.getState().equals("READY")) {
            if (!(shopifyClient.getOrder(refundPlan.getOrder().getId()).getRefunds().isEmpty())) {
                throw new BillingValidationException(
                        "Shopify order was refunded externally; reconcile before proceeding");
            }
            operation =
                    operationPersistenceService.update(
                            operation,
                            "REFUND_PAYMENT_PENDING",
                            null,
                            refundPlan.getOrder().getId(),
                            refundPlan.getReason(),
                            refundPlan.getStaffId());
            try {
                String id =
                        paymentTerminalClient.refundPayment(
                                refundPlan.getSale().getAuthorization(),
                                refundPlan.getAmountPaise(),
                                refundPlan.getReason());
                operation =
                        operationPersistenceService.update(
                                operation,
                                "REFUND_PAID",
                                id,
                                refundPlan.getOrder().getId(),
                                "Terminal refund processed",
                                refundPlan.getStaffId());
            } catch (UpstreamApiException exception) {
                return operationPersistenceService.update(
                        operation,
                        exception.isOutcomeUncertain()
                                ? "REFUND_PAYMENT_RECONCILIATION_REQUIRED"
                                : "READY",
                        null,
                        refundPlan.getOrder().getId(),
                        exception.getMessage(),
                        refundPlan.getStaffId());
            }
        }
        if (operation.getState().equals("REFUND_PAID")) {
            operation =
                    operationPersistenceService.update(
                            operation,
                            "REFUND_ORDER_PENDING",
                            operation.getExternalId(),
                            refundPlan.getOrder().getId(),
                            "Shopify refund intent persisted",
                            refundPlan.getStaffId());
            try {
                shopifyClient.createRefund(refundPlan, operation.getExternalId());
                return operationPersistenceService.update(
                        operation,
                        "COMPLETED",
                        operation.getExternalId(),
                        refundPlan.getOrder().getId(),
                        "Credit note recorded; frame restocked at billing store; lens scrapped",
                        refundPlan.getStaffId());
            } catch (UpstreamApiException exception) {
                return operationPersistenceService.update(
                        operation,
                        exception.isOutcomeUncertain()
                                ? "REFUND_ORDER_RECONCILIATION_REQUIRED"
                                : "REFUND_PAID",
                        operation.getExternalId(),
                        refundPlan.getOrder().getId(),
                        exception.getMessage(),
                        refundPlan.getStaffId());
            }
        }
        return operation;
    }

    public synchronized BillingOperation resume(String reference) {
        BillingOperation operation = get(reference);
        return operation.getKind().equals("SALE")
                ? continueCheckout(operation)
                : continueRefund(operation);
    }

    /**
     * Explicit operator-assisted recovery, verified against terminal status, never an unverified
     * override.
     */
    public synchronized BillingOperation reconcilePayment(
            String reference, String chargeId, String staffId, String reason) {
        BillingOperation operation = get(reference);
        if (reason == null || reason.isBlank()) {
            throw new BillingValidationException("Reconciliation reason required");
        }
        if (operation.getKind().equals("SALE")) {
            if (!(Set.of("PAYMENT_PENDING", "PAYMENT_RECONCILIATION_REQUIRED")
                    .contains(operation.getState()))) {
                throw new BillingValidationException("Sale is not awaiting payment reconciliation");
            }
            QuoteResponse quote =
                    operationPersistenceService.deserializeJson(
                            operation.getSnapshot(), QuoteResponse.class);
            quoteService.findStaffAtStore(quote.getStore(), staffId);
            PaymentChargeDto charge = paymentTerminalClient.getChargeStatus(chargeId);
            if (!(charge.getOrderReference().equals(operation.getReference())
                    && charge.getAmountPaise() == quote.getTotalPaise()
                    && "captured".equals(charge.getStatus())
                    && charge.getRefundedPaise() == 0)) {
                throw new BillingValidationException(
                        "Terminal evidence does not match this unrefunded captured checkout");
            }
            operationPersistenceService.audit(
                    reference, staffId, "PAYMENT_EVIDENCE_VERIFIED", reason);
            operation =
                    operationPersistenceService.update(
                            operation,
                            "PAID",
                            charge.getId(),
                            null,
                            "Terminal status verified by " + staffId,
                            staffId);
            return continueCheckout(operation);
        }
        if (!(Set.of("REFUND_PAYMENT_PENDING", "REFUND_PAYMENT_RECONCILIATION_REQUIRED")
                .contains(operation.getState()))) {
            throw new BillingValidationException("Refund is not awaiting payment reconciliation");
        }
        RefundPlanDto refundPlan =
                operationPersistenceService.deserializeJson(
                        operation.getSnapshot(), RefundPlanDto.class);
        quoteService.findStaffAtStore(refundPlan.getOrder().getStore(), staffId);
        if (!chargeId.equals(refundPlan.getSale().getAuthorization())) {
            throw new BillingValidationException(
                    "Charge must be the original sale's terminal charge");
        }
        PaymentChargeDto charge = paymentTerminalClient.getChargeStatus(chargeId);
        if (!(charge.getAmountPaise() == refundPlan.getAmountPaise()
                && charge.getRefundedPaise() == refundPlan.getAmountPaise())) {
            throw new BillingValidationException(
                    "Terminal does not confirm a full refund; no further write sent");
        }
        operationPersistenceService.audit(reference, staffId, "REFUND_EVIDENCE_VERIFIED", reason);
        operation =
                operationPersistenceService.update(
                        operation,
                        "REFUND_PAID",
                        "status-confirmed:" + chargeId,
                        refundPlan.getOrder().getId(),
                        "Original charge confirms full refund; provider refund ID unavailable",
                        staffId);
        return continueRefund(operation);
    }

    public synchronized BillingOperation cancel(String reference, String staff, String reason) {
        BillingOperation operation = get(reference);
        if (!(operation.getKind().equals("SALE") && operation.getState().equals("READY"))) {
            throw new BillingValidationException(
                    "Only a definitely uncharged READY sale can be cancelled");
        }
        QuoteResponse quote =
                operationPersistenceService.deserializeJson(
                        operation.getSnapshot(), QuoteResponse.class);
        quoteService.findStaffAtStore(quote.getStore(), staff);
        if (reason == null || reason.isBlank()) {
            throw new BillingValidationException("Cancellation reason required");
        }
        return operationPersistenceService.update(
                operation, "CANCELLED", null, null, reason, staff);
    }

    public BillingOperation get(String reference) {
        BillingOperation operation = operationPersistenceService.find(reference);
        if (operation == null) {
            throw new BillingValidationException("Unknown operation reference");
        }
        return operation;
    }

    private static long findLastInvoiceNumber(List<String> invoices, String prefix) {
        long max = 0;
        for (String invoice : invoices)
            if (invoice != null && invoice.startsWith(prefix)) {
                String suffix = invoice.substring(prefix.length());
                if (suffix.matches("[0-9]+")) max = Math.max(max, Long.parseLong(suffix));
            }
        return max;
    }
}
