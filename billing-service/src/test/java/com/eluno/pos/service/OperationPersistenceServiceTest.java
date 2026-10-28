package com.eluno.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eluno.pos.dto.CartLineDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.entity.BillingOperation;
import com.eluno.pos.entity.InvoiceSeries;
import com.eluno.pos.exception.BillingValidationException;
import com.eluno.pos.repository.AuditEventRepository;
import com.eluno.pos.repository.BillingOperationRepository;
import com.eluno.pos.repository.InvoiceSeriesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:jpa-ledger;DB_CLOSE_DELAY=-1",
            "debug=false",
            "logging.level.org.springframework=INFO",
            "logging.level.org.hibernate=INFO"
        })
class OperationPersistenceServiceTest {
    @Autowired private OperationPersistenceService persistenceService;
    @Autowired private InvoiceSeriesRepository invoiceSeriesRepository;
    @Autowired private BillingOperationRepository billingOperationRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void persistsInvoiceStateAndAuditThroughHibernate() {
        BillingOperation operation =
                persistenceService.reserve(
                        "jpa-save",
                        "SALE",
                        Map.of("total", 500),
                        "ELU/JPATEST/FY26/",
                        4,
                        Map.of("total", 500),
                        "S105");

        assertThat(operation.getInvoiceNo()).isEqualTo("ELU/JPATEST/FY26/00005");
        assertThat(billingOperationRepository.findById("jpa-save")).isPresent();
        assertThat(
                        invoiceSeriesRepository
                                .findById("ELU/JPATEST/FY26/")
                                .orElseThrow()
                                .getNextNumber())
                .isEqualTo(6);
        assertThat(auditEventRepository.findByReferenceOrderByIdAsc("jpa-save")).hasSize(1);

        persistenceService.update(operation, "COMPLETED", "charge-1", 123L, "Confirmed", "S105");
        assertThat(persistenceService.find("jpa-save").getState()).isEqualTo("COMPLETED");
        assertThat(persistenceService.unresolved("JPATEST")).isZero();
        assertThat(auditEventRepository.findByReferenceOrderByIdAsc("jpa-save")).hasSize(2);

        assertThatThrownBy(
                        () ->
                                persistenceService.reserve(
                                        "jpa-save",
                                        "SALE",
                                        Map.of(),
                                        "ELU/JPATEST/FY26/",
                                        4,
                                        Map.of(),
                                        "S105"))
                .isInstanceOf(BillingValidationException.class);
        assertThat(
                        invoiceSeriesRepository
                                .findById("ELU/JPATEST/FY26/")
                                .orElseThrow()
                                .getNextNumber())
                .isEqualTo(6);
    }

    @Test
    void failedInvoiceInsertRollsBackCounterAndAudit() {
        String prefix = "ELU/ROLLBACK/FY26/";
        persistenceService.reserve("jpa-original", "SALE", Map.of(), prefix, 0, Map.of(), "S105");
        // Simulate a stale counter to force the real unique invoice constraint to reject the write.
        invoiceSeriesRepository.saveAndFlush(new InvoiceSeries(prefix, 1));
        long operationCount = billingOperationRepository.count();
        long auditCount = auditEventRepository.count();

        assertThatThrownBy(
                        () ->
                                persistenceService.reserve(
                                        "jpa-conflict",
                                        "SALE",
                                        Map.of(),
                                        prefix,
                                        0,
                                        Map.of(),
                                        "S105"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(billingOperationRepository.count()).isEqualTo(operationCount);
        assertThat(billingOperationRepository.findById("jpa-conflict")).isEmpty();
        assertThat(auditEventRepository.count()).isEqualTo(auditCount);
        assertThat(invoiceSeriesRepository.findById(prefix).orElseThrow().getNextNumber())
                .isEqualTo(1);
    }

    @Test
    void lombokBeansKeepTheirJsonContract() throws Exception {
        CartLineDto line =
                CartLineDto.builder()
                        .variant(ProductVariantDto.builder().id(42).build())
                        .pairRef("pair-1")
                        .build();

        assertThat(line.getVariant().getId()).isEqualTo(42);
        line.setPairRef("pair-2");
        String json = objectMapper.writeValueAsString(line);
        assertThat(objectMapper.readTree(json).path("pairRef").asText()).isEqualTo("pair-2");
        assertThat(objectMapper.readValue(json, CartLineDto.class)).isEqualTo(line);
    }
}
