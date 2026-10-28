package com.eluno.pos.service;

import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.client.StoreToolClient;
import com.eluno.pos.dto.CartLineDto;
import com.eluno.pos.dto.CustomerDto;
import com.eluno.pos.dto.CustomerLookupResponse;
import com.eluno.pos.dto.OfferEvaluationResultDto;
import com.eluno.pos.dto.PrescriptionDto;
import com.eluno.pos.dto.PricedCartLineDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.dto.QuoteResponse;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.dto.StoreDto;
import com.eluno.pos.dto.TaxBreakdownDto;
import com.eluno.pos.dto.TaxRateDto;
import com.eluno.pos.dto.request.CartPairRequest;
import com.eluno.pos.dto.request.CartRequest;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.repository.JsonRuleRepository;
import com.eluno.pos.validator.PrescriptionValidator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuoteService {
    private final ShopifyClient shopifyClient;
    private final StoreToolClient storeToolClient;
    private final JsonRuleRepository ruleRepository;
    private final TaxService taxService;
    private final OfferService offerService;
    private final PrescriptionValidator prescriptionValidator;
    private final Clock clock;

    public StoreDto findStoreByCode(String storeCode) {
        return shopifyClient.getStores().stream()
                .filter(store -> store.getCode().equals(storeCode))
                .findFirst()
                .orElseThrow(() -> new BillingValidationException("Unknown store " + storeCode));
    }

    public StaffDto findStaffAtStore(StoreDto store, String staffId) {
        return storeToolClient.getStaffByStore(store).stream()
                .filter(staff -> staff.getId().equals(staffId))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BillingValidationException(
                                        "Staff member does not belong to this store"));
    }

    public PrescriptionDto findLatestValidPrescription(CustomerDto customer) {
        LocalDate today = LocalDate.now(clock);
        return storeToolClient.getPrescriptionsByPhone(customer.getPhone()).stream()
                .filter(prescription -> prescription.getCustomerId() == customer.getId())
                .filter(prescription -> !prescription.getDate().isAfter(today))
                .filter(prescription -> !prescription.getValidUntil().isBefore(today))
                .max(
                        Comparator.comparing(PrescriptionDto::getDate)
                                .thenComparing(PrescriptionDto::getId))
                .orElse(null);
    }

    public CustomerLookupResponse lookupCustomer(String phone) {
        CustomerDto customer = shopifyClient.findCustomerByPhone(phone);
        PrescriptionDto prescription = findLatestValidPrescription(customer);
        String message =
                prescription == null
                        ? "No valid prescription; arrange an eye test before billing lenses"
                        : "Latest valid prescription selected";
        return new CustomerLookupResponse(customer, prescription, message);
    }

    public QuoteResponse createQuote(CartRequest request) {
        StoreDto store = findStoreByCode(request.getStoreCode());
        findStaffAtStore(store, request.getStaffId());
        CustomerDto customer = shopifyClient.findCustomerByPhone(request.getPhone());
        PrescriptionDto prescription = findLatestValidPrescription(customer);
        LocalDate billingDate = LocalDate.now(clock);
        List<ProductVariantDto> catalog = shopifyClient.getCatalog();

        List<CartLineDto> cartLines = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        Set<String> usedPairReferences = new HashSet<>();
        Map<Long, Integer> requiredFrameQuantities = new HashMap<>();

        for (CartPairRequest pair : request.getPairs()) {
            if (!usedPairReferences.add(pair.getPairRef())) {
                throw new BillingValidationException("Each pairRef must be unique");
            }
            if (pair.getFrameVariantId() == null && pair.getLensVariantId() == null) {
                throw new BillingValidationException("Each pair needs a frame or lens");
            }

            if (pair.getFrameVariantId() != null) {
                ProductVariantDto frame =
                        findActiveVariant(catalog, pair.getFrameVariantId(), "frame");
                if (pair.getLensVariantId() != null && !frame.isRxAble()) {
                    throw new BillingValidationException(
                            "Selected frame cannot carry a prescription lens; choose an optical frame");
                }
                requiredFrameQuantities.merge(frame.getId(), 1, Integer::sum);
                cartLines.add(new CartLineDto(frame, pair.getPairRef(), null));
            }

            if (pair.getLensVariantId() != null) {
                ProductVariantDto lens =
                        findActiveVariant(catalog, pair.getLensVariantId(), "lens");
                warnings.addAll(prescriptionValidator.validate(prescription, lens, billingDate));
                cartLines.add(new CartLineDto(lens, pair.getPairRef(), prescription));
            }
        }

        validateFrameStock(catalog, store, requiredFrameQuantities);
        StaffDto manager = findRequestedManager(request, store);
        OfferEvaluationResultDto offerResult =
                offerService.evaluate(
                        cartLines,
                        customer,
                        billingDate,
                        ruleRepository.getOffers(),
                        request.getManagerDiscountBps(),
                        manager,
                        request.getReasonCode());

        List<TaxRateDto> taxRates = ruleRepository.taxRates();
        List<PricedCartLineDto> pricedLines = new ArrayList<>();
        long grossPaise = 0;
        long discountPaise = 0;
        long taxablePaise = 0;
        long cgstPaise = 0;
        long sgstPaise = 0;

        for (int lineIndex = 0; lineIndex < cartLines.size(); lineIndex++) {
            CartLineDto line = cartLines.get(lineIndex);
            long lineDiscountPaise = offerResult.getDiscounts()[lineIndex];
            long discountedPricePaise = line.getVariant().getPricePaise() - lineDiscountPaise;
            TaxBreakdownDto tax =
                    taxService.calculate(
                            discountedPricePaise,
                            line.getVariant().getHsn(),
                            billingDate,
                            taxRates);

            pricedLines.add(
                    new PricedCartLineDto(
                            line,
                            lineDiscountPaise,
                            tax,
                            offerResult.getAllocations().get(lineIndex)));
            grossPaise += line.getVariant().getPricePaise();
            discountPaise += lineDiscountPaise;
            taxablePaise += tax.getTaxablePaise();
            cgstPaise += tax.getCgstPaise();
            sgstPaise += tax.getSgstPaise();
        }

        long totalPaise = grossPaise - discountPaise;
        if (totalPaise <= 0) {
            throw new BillingValidationException(
                    "Zero-value checkout is outside this exercise's paid-sale flow");
        }

        return new QuoteResponse(
                store,
                customer,
                prescription,
                billingDate,
                request.getStaffId(),
                request.getChannel(),
                request.getPaymentMethod(),
                request.getManagerId(),
                request.getReasonCode(),
                pricedLines,
                offerResult.getDecisions(),
                List.copyOf(warnings),
                grossPaise,
                discountPaise,
                taxablePaise,
                cgstPaise,
                sgstPaise,
                totalPaise);
    }

    private StaffDto findRequestedManager(CartRequest request, StoreDto store) {
        if (request.getManagerDiscountBps() == null
                && (request.getManagerId() != null || request.getReasonCode() != null)) {
            throw new BillingValidationException(
                    "Manager identity/reason supplied without a manual discount request");
        }
        return request.getManagerId() == null
                ? null
                : findStaffAtStore(store, request.getManagerId());
    }

    private void validateFrameStock(
            List<ProductVariantDto> catalog,
            StoreDto store,
            Map<Long, Integer> requiredFrameQuantities) {
        for (Map.Entry<Long, Integer> requirement : requiredFrameQuantities.entrySet()) {
            ProductVariantDto frame = findActiveVariant(catalog, requirement.getKey(), "frame");
            int availableQuantity = shopifyClient.getAvailableStock(frame, store);
            if (availableQuantity < requirement.getValue()) {
                throw new BillingValidationException(
                        frame.getSku()
                                + " has insufficient stock at "
                                + store.getCode()
                                + "; choose another frame/store");
            }
        }
    }

    private ProductVariantDto findActiveVariant(
            List<ProductVariantDto> catalog, long variantId, String lineType) {
        return catalog.stream()
                .filter(variant -> variant.getId() == variantId)
                .filter(variant -> variant.getLineType().equals(lineType))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BillingValidationException(
                                        "No active " + lineType + " variant " + variantId));
    }
}
