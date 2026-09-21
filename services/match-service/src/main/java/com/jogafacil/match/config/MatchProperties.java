package com.jogafacil.match.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do dominio de partidas (prefixo {@code jogafacil}).
 *
 * @param matchTable                 tabela DynamoDB das partidas
 * @param reservationServiceBaseUrl  URL base logica do reservation-service
 *                                   (LocalStack/local ou Cloud Map na AWS)
 */
@ConfigurationProperties(prefix = "jogafacil")
public record MatchProperties(
        String matchTable,
        String reservationServiceBaseUrl) {
}
