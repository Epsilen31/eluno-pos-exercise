package com.eluno.pos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eluno.pos.client.PaymentTerminalClient;
import com.eluno.pos.client.ShopifyClient;
import com.eluno.pos.dto.PaymentChargeDto;
import com.eluno.pos.dto.request.CartPairRequest;
import com.eluno.pos.dto.request.CartRequest;
import com.eluno.pos.dto.request.CheckoutRequest;
import com.eluno.pos.dto.request.RefundRequest;
import com.eluno.pos.exception.UpstreamApiException;
import com.eluno.pos.service.CheckoutService;
import com.eluno.pos.service.OperationPersistenceService;
import com.eluno.pos.service.QuoteService;
import com.eluno.pos.service.RefundService;
import com.eluno.pos.service.ReportingService;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1",
            "debug=false",
            "logging.level.org.springframework=INFO"
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PosIntegrationTest {
    private static Process mock;

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String python =
                System.getenv()
                        .getOrDefault(
                                "PYTHON",
                                System.getProperty("os.name").toLowerCase().contains("win")
                                        ? "python"
                                        : "python3");
        Files.createDirectories(Path.of("target"));
        mock =
                new ProcessBuilder(
                                python,
                                "../starter-kit/mock-apis/mock_server.py",
                                "--port",
                                String.valueOf(port))
                        .redirectErrorStream(true)
                        .redirectOutput(Path.of("target/integration-mock.log").toFile())
                        .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> mock.destroy()));
        var client = HttpClient.newHttpClient();
        String url = "http://localhost:" + port;
        boolean ready = false;
        for (int i = 0; i < 100; i++) {
            try {
                if (client.send(
                                        HttpRequest.newBuilder(URI.create(url + "/health"))
                                                .GET()
                                                .build(),
                                        HttpResponse.BodyHandlers.discarding())
                                .statusCode()
                        == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
                Thread.sleep(50);
            }
        }
        if (!ready)
            throw new IllegalStateException(
                    "Python mock failed; inspect target/integration-mock.log");
        registry.add("pos.upstream", () -> url);
    }

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-26T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired QuoteService quotes;
    @Autowired CheckoutService service;
    @Autowired OperationPersistenceService ledger;
    @Autowired ReportingService reports;
    @Autowired TestRestTemplate api;
    @Autowired RefundService refundService;
    @MockitoSpyBean ShopifyClient shopify;
    @MockitoSpyBean PaymentTerminalClient terminal;

    @AfterEach
    void resetSpies() {
        reset(shopify, terminal);
    }

    @AfterAll
    static void stopMock() throws Exception {
        if (mock != null) {
            mock.destroy();
            mock.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private CartRequest cart() {
        return new CartRequest(
                "+919000000005",
                "HSR",
                "S105",
                "walk_in",
                "upi",
                List.of(new CartPairRequest("pair1", 91000003L, 91000083L)),
                null,
                null,
                null);
    }

    private CheckoutRequest request(String ref) {
        var c = cart();
        return new CheckoutRequest(ref, c, quotes.createQuote(c).getTotalPaise());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void apiCheckoutRefundInvoiceAndReportAgainstFlakyMock() {
        var q = quotes.createQuote(cart());
        assertThat(q.getPrescription().getId()).isEqualTo("RX5007");
        assertThat(q.getTaxablePaise() + q.getCgstPaise() + q.getSgstPaise())
                .isEqualTo(q.getTotalPaise());
        var frame = q.getLines().getFirst().getItem().getVariant();
        int before = shopify.getAvailableStock(frame, q.getStore());
        var request = new CheckoutRequest("integration", cart(), q.getTotalPaise());
        var response = api.postForEntity("/api/checkouts", request, JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("state").asText()).isEqualTo("COMPLETED");
        long id = body.path("orderId").asLong();
        var order = shopify.getOrder(id);
        assertThat(order.getInvoiceNo()).startsWith("ELU/HSR/FY26/");
        assertThat(shopify.getAvailableStock(frame, q.getStore())).isEqualTo(before - 1);
        assertThat(order.getLines().get(1).getPrescriptionId()).isEqualTo("RX5007");
        assertThat(api.getForObject("/api/orders/" + id + "/invoice", String.class))
                .contains("RX5007", "90015000", "CGST");
        assertThat(
                        api.postForObject("/api/checkouts", request, JsonNode.class)
                                .path("orderId")
                                .asLong())
                .isEqualTo(id);
        verify(terminal, times(1)).chargePayment(anyLong(), anyString(), anyString());
        var refundRequest = new RefundRequest("integration-return", "S105", "CHANGED_MIND");
        var refund =
                api.postForObject("/api/orders/" + id + "/refund", refundRequest, JsonNode.class);
        assertThat(refund.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(refund.path("invoiceNo").asText()).contains("/CN/");
        assertThat(shopify.getAvailableStock(frame, q.getStore())).isEqualTo(before);
        assertThat(
                        api.postForObject(
                                        "/api/orders/" + id + "/refund",
                                        refundRequest,
                                        JsonNode.class)
                                .path("state")
                                .asText())
                .isEqualTo("COMPLETED");
        verify(terminal, times(1)).refundPayment(anyString(), anyLong(), anyString());
        var returned = shopify.getOrder(id);
        assertThat(returned.getRefunds()).hasSize(1);
        assertThat(returned.getRefunds().getFirst().getAmountPaise()).isEqualTo(q.getTotalPaise());
        var date = order.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDate();
        var report = reports.dayEnd("HSR", date);
        assertThat(report.getReconciliationIssues()).isEmpty();
        assertThat(report.getSalesPaise()).isEqualTo(q.getTotalPaise());
        assertThat(report.getNetPaise()).isZero();
        assertThat(report.getChannels().get("walk_in").getNetPaise()).isZero();
        assertThat(ledger.audit("sale-integration")).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void uncertainOrderWriteIsRecoveredWithoutASecondCreateOrCharge() {
        var request = request("lost-order-response");
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new UpstreamApiException("response lost after commit", true);
                        })
                .when(shopify)
                .createOrder(any(), anyString(), anyString(), anyString());
        var pending = service.checkout(request);
        assertThat(pending.getState()).isEqualTo("ORDER_RECONCILIATION_REQUIRED");
        // New service instance simulates loss of all in-process state; only the ledger survives.
        var restarted = new CheckoutService(shopify, terminal, quotes, ledger, refundService);
        var recovered = restarted.checkout(request);
        assertThat(recovered.getState()).isEqualTo("COMPLETED");
        verify(shopify, times(1)).createOrder(any(), anyString(), anyString(), anyString());
        verify(terminal, times(1)).chargePayment(anyLong(), anyString(), anyString());
        assertThat(
                        service.refund(
                                        recovered.getOrderId(),
                                        new RefundRequest("recovered-cleanup", "S105", "RETURN"))
                                .getState())
                .isEqualTo("COMPLETED");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void uncertainPaymentNeverChargesAgainAndCannotBeCancelled() {
        var request = request("lost-payment-response");
        var calls = new AtomicInteger();
        var captured = new AtomicReference<PaymentChargeDto>();
        doAnswer(
                        call -> {
                            calls.incrementAndGet();
                            captured.set((PaymentChargeDto) call.callRealMethod());
                            throw new UpstreamApiException("terminal response lost", true);
                        })
                .when(terminal)
                .chargePayment(anyLong(), anyString(), anyString());
        var first = service.checkout(request);
        assertThat(first.getState()).isEqualTo("PAYMENT_RECONCILIATION_REQUIRED");
        var second = service.checkout(request);
        assertThat(second.getState()).isEqualTo("PAYMENT_RECONCILIATION_REQUIRED");
        assertThat(calls.get()).isEqualTo(1);
        verify(shopify, never()).createOrder(any(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.cancel(first.getReference(), "S105", "CANCEL"))
                .hasMessageContaining("uncharged");
        var reconciled =
                service.reconcilePayment(
                        first.getReference(),
                        captured.get().getId(),
                        "S105",
                        "VERIFIED_TERMINAL_RECEIPT");
        assertThat(reconciled.getState()).isEqualTo("COMPLETED");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(
                        service.refund(
                                        reconciled.getOrderId(),
                                        new RefundRequest(
                                                "payment-recovered-cleanup", "S105", "RETURN"))
                                .getState())
                .isEqualTo("COMPLETED");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void conflictingPayloadAndStalePriceAreRejectedBeforePayment() {
        var c = cart();
        var total = quotes.createQuote(c).getTotalPaise();
        assertThatThrownBy(() -> service.checkout(new CheckoutRequest("wrong-price", c, total - 1)))
                .hasMessageContaining("Price changed");
        var existing =
                ledger.deserializeJson(
                        ledger.find("sale-integration").getRequestJson(), CheckoutRequest.class);
        assertThatThrownBy(
                        () ->
                                service.checkout(
                                        new CheckoutRequest(
                                                existing.getReference(),
                                                existing.getCart(),
                                                existing.getExpectedTotalPaise() + 1)))
                .hasMessageContaining("different checkout payload");
        verify(terminal, never()).chargePayment(anyLong(), anyString(), anyString());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void simultaneousSameReferenceCreatesOneOrder() throws Exception {
        var request = request("concurrent");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.checkout(request));
            var b = pool.submit(() -> service.checkout(request));
            assertThat(a.get(30, TimeUnit.SECONDS).getOrderId())
                    .isEqualTo(b.get(30, TimeUnit.SECONDS).getOrderId());
        }
        verify(terminal, times(1)).chargePayment(anyLong(), anyString(), anyString());
        verify(shopify, times(1)).createOrder(any(), anyString(), anyString(), anyString());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void ambiguousRefundResponseDoesNotRepeatRestock() {
        var sale = service.checkout(request("refund-recovery"));
        var before = shopify.getOrder(sale.getOrderId());
        var frame =
                shopify.getCatalog().stream()
                        .filter(v -> v.getId() == 91000003L)
                        .findFirst()
                        .orElseThrow();
        int stock = shopify.getAvailableStock(frame, before.getStore());
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new UpstreamApiException("refund response lost", true);
                        })
                .when(shopify)
                .createRefund(any(), anyString());
        var req = new RefundRequest("refund-recovery", "S105", "RETURN");
        assertThat(service.refund(sale.getOrderId(), req).getState())
                .isEqualTo("REFUND_ORDER_RECONCILIATION_REQUIRED");
        assertThat(service.refund(sale.getOrderId(), req).getState()).isEqualTo("COMPLETED");
        assertThat(shopify.getAvailableStock(frame, before.getStore())).isEqualTo(stock + 1);
        verify(shopify, times(1)).createRefund(any(), anyString());
        verify(terminal, times(1)).refundPayment(anyString(), anyLong(), anyString());
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void malformedApiInputFailsValidation() {
        var response =
                api.postForEntity("/api/checkouts", Map.of("reference", "bad"), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void uncertainTerminalRefundCanBeRecoveredByOriginalChargeStatus() {
        var sale = service.checkout(request("terminal-refund-recovery"));
        var original = shopify.getOrder(sale.getOrderId());
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new UpstreamApiException("terminal refund response lost", true);
                        })
                .when(terminal)
                .refundPayment(anyString(), anyLong(), anyString());
        var req = new RefundRequest("terminal-refund-recovery", "S105", "RETURN");
        var pending = service.refund(sale.getOrderId(), req);
        assertThat(pending.getState()).isEqualTo("REFUND_PAYMENT_RECONCILIATION_REQUIRED");
        assertThat(service.refund(sale.getOrderId(), req).getState())
                .isEqualTo("REFUND_PAYMENT_RECONCILIATION_REQUIRED");
        assertThat(
                        service.reconcilePayment(
                                        pending.getReference(),
                                        original.getSales().getFirst().getAuthorization(),
                                        "S105",
                                        "VERIFIED_REFUND_STATUS")
                                .getState())
                .isEqualTo("COMPLETED");
        verify(terminal, times(1)).refundPayment(anyString(), anyLong(), anyString());
        assertThat(shopify.getOrder(sale.getOrderId()).getRefunds()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void absentUncertainOrderRemainsPendingRatherThanReposting() {
        var request = request("unresolved-order");
        doThrow(new UpstreamApiException("unknown order outcome", true))
                .when(shopify)
                .createOrder(any(), anyString(), anyString(), anyString());
        var first = service.checkout(request);
        assertThat(first.getState()).isEqualTo("ORDER_RECONCILIATION_REQUIRED");
        assertThat(service.resume(first.getReference()).getState())
                .isEqualTo("ORDER_RECONCILIATION_REQUIRED");
        verify(shopify, times(1)).createOrder(any(), anyString(), anyString(), anyString());
        verify(terminal, times(1)).chargePayment(anyLong(), anyString(), anyString());
        var report = reports.dayEnd("HSR", LocalDate.of(2026, 10, 26));
        assertThat(report.getUnresolvedOperations()).isEqualTo(1);
    }
}
