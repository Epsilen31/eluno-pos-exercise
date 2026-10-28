package com.eluno.pos.service;

import com.eluno.pos.dto.OrderDto;
import com.eluno.pos.dto.OrderLineDto;
import com.eluno.pos.dto.RefundPlanDto;
import com.eluno.pos.dto.SaleTransactionDto;
import com.eluno.pos.exception.BillingValidationException;
import org.springframework.stereotype.Service;

@Service
public class RefundService {

    /** Reverse original amounts; never recalculate refunds using today's tax rates or offers. */
    public RefundPlanDto calculateFullRefund(
            OrderDto order, String creditNoteNumber, String reasonCode, String staffId) {
        if (order.isCancelled()) {
            throw new BillingValidationException(
                    "Cancelled orders cannot be refunded through this flow");
        }
        if (!order.getRefunds().isEmpty()) {
            throw new BillingValidationException(
                    "Order already has a refund; full refund only is supported");
        }
        if (order.getSales().size() != 1) {
            throw new BillingValidationException(
                    "Full refund currently requires one captured sale transaction");
        }

        long refundAmountPaise = 0;
        long cgstPaise = 0;
        long sgstPaise = 0;

        for (OrderLineDto line : order.getLines()) {
            validateRefundableLine(line);
            refundAmountPaise += line.getTotalPaise();
            cgstPaise += line.getCgstPaise();
            sgstPaise += line.getSgstPaise();
        }

        SaleTransactionDto originalSale = order.getSales().getFirst();
        if (refundAmountPaise <= 0
                || refundAmountPaise != order.getTotalPaise()
                || refundAmountPaise != originalSale.getAmountPaise()) {
            throw new BillingValidationException(
                    "Original lines and captured payment do not reconcile");
        }

        long totalTaxPaise = cgstPaise + sgstPaise;
        if (totalTaxPaise != order.getTaxPaise() || totalTaxPaise > refundAmountPaise) {
            throw new BillingValidationException("Original tax does not reconcile");
        }

        long taxableAmountPaise = refundAmountPaise - totalTaxPaise;
        return new RefundPlanDto(
                order,
                creditNoteNumber,
                reasonCode,
                staffId,
                refundAmountPaise,
                taxableAmountPaise,
                cgstPaise,
                sgstPaise,
                originalSale);
    }

    private void validateRefundableLine(OrderLineDto line) {
        if (line.getQuantity() <= 0 || line.getTotalPaise() < 0) {
            throw new BillingValidationException("Invalid original invoice line");
        }

        boolean isFrame = "frame".equals(line.getLineType());
        boolean isLens = "lens".equals(line.getLineType());
        if (!isFrame && !isLens) {
            throw new BillingValidationException(
                    "Unknown line type; restock policy requires review");
        }
    }
}
