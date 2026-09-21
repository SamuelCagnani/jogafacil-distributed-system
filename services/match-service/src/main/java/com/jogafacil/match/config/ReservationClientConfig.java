package com.jogafacil.match.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configura o {@link RestClient} usado para falar com o reservation-service.
 *
 * <p>A URL base vem de {@code jogafacil.reservation-service.base-url}: local
 * aponta para {@code http://localhost:8081} e na AWS aponta para o nome logico
 * do servico registrado no Cloud Map, evitando IPs fixos.</p>
 */
@Configuration
public class ReservationClientConfig {

    @Bean
    public RestClient reservationRestClient(MatchProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.reservationServiceBaseUrl())
                .build();
    }
}
