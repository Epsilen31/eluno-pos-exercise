package com.eluno.pos.client.impl;

import com.eluno.pos.client.ExternalApiClient;
import com.eluno.pos.client.StoreToolClient;
import com.eluno.pos.dto.EyePrescriptionDto;
import com.eluno.pos.dto.PrescriptionDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.dto.StoreDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreToolClientImpl implements StoreToolClient {
    private final ExternalApiClient externalApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PrescriptionDto> getPrescriptionsByPhone(String phone) {
        var result = new ArrayList<PrescriptionDto>();
        for (var node :
                externalApiClient
                        .get(
                                "/smt/prescriptions?phone="
                                        + URLEncoder.encode(phone, StandardCharsets.UTF_8))
                        .path("prescriptions"))
            result.add(
                    new PrescriptionDto(
                            node.path("rx_id").asText(),
                            node.path("customer_id").asLong(),
                            LocalDate.parse(node.path("date").asText()),
                            LocalDate.parse(node.path("valid_until").asText()),
                            node.path("source").asText(),
                            readEyePrescription(node.path("od")),
                            readEyePrescription(node.path("os")),
                            objectMapper.convertValue(
                                    node.path("pd"), new TypeReference<Map<String, Object>>() {})));
        return result;
    }

    @Override
    public List<StaffDto> getStaffByStore(StoreDto store) {
        var result = new ArrayList<StaffDto>();
        for (var node :
                externalApiClient
                        .get(
                                "/smt/staff?store="
                                        + URLEncoder.encode(
                                                store.getCode(), StandardCharsets.UTF_8))
                        .path("staff"))
            result.add(
                    new StaffDto(
                            node.path("staff_id").asText(),
                            node.path("role").asText(),
                            node.path("can_override_discount").asBoolean()));
        return result;
    }

    private static EyePrescriptionDto readEyePrescription(JsonNode node) {
        return new EyePrescriptionDto(
                node.hasNonNull("sph") ? node.get("sph").decimalValue() : null,
                node.hasNonNull("cyl") ? node.get("cyl").decimalValue() : null,
                node.hasNonNull("axis") ? node.get("axis").intValue() : null,
                node.hasNonNull("add") ? node.get("add").decimalValue() : null);
    }
}
