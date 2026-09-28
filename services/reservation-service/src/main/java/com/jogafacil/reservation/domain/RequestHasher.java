package com.jogafacil.reservation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcula um hash estavel do corpo de uma requisicao.
 *
 * <p>O hash e gravado junto com a {@code Idempotency-Key}; se a mesma chave
 * chegar com um corpo diferente, a requisicao e rejeitada com
 * {@code IDEMPOTENCY_KEY_REUSED}, evitando reutilizacao acidental de chave.</p>
 */
@Component
public class RequestHasher {

    private final ObjectMapper objectMapper;

    public RequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(Object request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Could not hash the request body", failure);
        }
    }
}
