package com.jogafacil.reservation.persistence;

import java.util.Optional;

/**
 * Armazena o resultado de operacoes idempotentes.
 *
 * <p>O ciclo tipico e {@link #claim} (reserva a chave de forma condicional),
 * execucao da operacao, {@link #complete} (grava a resposta) e, se a operacao
 * falhar, {@link #release} (libera a chave para nova tentativa).</p>
 */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String operationId);

    boolean claim(String operationId, String requestHash);

    void complete(String operationId, int responseStatus, String responseBody);

    void release(String operationId);
}
