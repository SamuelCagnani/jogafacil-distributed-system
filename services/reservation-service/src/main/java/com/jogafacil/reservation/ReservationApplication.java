package com.jogafacil.reservation;

import com.jogafacil.reservation.config.ReservationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Aplicacao do reservation-service.
 *
 * <p>Responsavel por confirmar e cancelar reservas de horarios de quadra. A
 * unicidade de {@code (courtId, slotId)} e garantida por escrita condicional no
 * DynamoDB, e as requisicoes de criacao sao idempotentes por {@code Idempotency-Key}.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ReservationProperties.class)
public class ReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }
}
