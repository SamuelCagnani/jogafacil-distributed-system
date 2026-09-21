package com.jogafacil.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do dominio de reservas (prefixo {@code jogafacil} no
 * {@code application.yml}).
 *
 * @param reservationTable  tabela DynamoDB das reservas
 * @param idempotencyTable  tabela DynamoDB dos registros de idempotencia
 * @param reservationIdIndex nome do GSI que mapeia {@code reservationId}
 * @param defaultOwnerId    dono usado quando a requisicao nao informa {@code ownerId}
 *                          (provisorio ate a autenticacao da Entrega 4)
 */
@ConfigurationProperties(prefix = "jogafacil")
public record ReservationProperties(
        String reservationTable,
        String idempotencyTable,
        String reservationIdIndex,
        String defaultOwnerId) {
}
