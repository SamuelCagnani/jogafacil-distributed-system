package com.jogafacil.match;

import com.jogafacil.match.config.MatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Aplicacao do match-service.
 *
 * <p>Cria partidas e delega a ocupacao do horario ao reservation-service por
 * chamada REST sincrona, demonstrando a comunicacao entre processos desta
 * entrega.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(MatchProperties.class)
public class MatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchApplication.class, args);
    }
}
