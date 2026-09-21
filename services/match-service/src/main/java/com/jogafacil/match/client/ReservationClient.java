package com.jogafacil.match.client;

import com.jogafacil.contracts.api.ApiMediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

/**
 * Cliente REST sincrono do reservation-service.
 *
 * <p>E a comunicacao entre processos exigida nesta entrega: ao criar uma
 * partida, o match-service chama {@code POST /reservations} para segurar o
 * horario antes de gravar a partida. A chamada e sincrona porque a resposta
 * (sucesso ou conflito) e necessaria para concluir a operacao.</p>
 */
@Component
public class ReservationClient {

    private static final MediaType V1 = MediaType.parseMediaType(ApiMediaType.V1);

    private final RestClient restClient;

    public ReservationClient(RestClient reservationRestClient) {
        this.restClient = reservationRestClient;
    }

    /**
     * Reserva o horario de uma quadra no reservation-service.
     *
     * @param operationKey valor enviado no header {@code Idempotency-Key}
     * @throws ReservationConflictException      se o horario ja estiver reservado (HTTP 409)
     * @throws ReservationServiceUnavailableException se o servico estiver fora ou responder 5xx
     */
    public ReservedSlot reserve(String operationKey, ReservationRequest request) {
        try {
            ReservedSlot response = restClient.post()
                    .uri("/reservations")
                    .header("Idempotency-Key", operationKey)
                    .contentType(V1)
                    .accept(V1)
                    .body(request)
                    .retrieve()
                    .body(ReservedSlot.class);

            if (response == null) {
                throw new ReservationServiceUnavailableException("Empty response from reservation-service");
            }
            return response;
        } catch (RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ReservationConflictException(
                        "Court slot is already reserved", responseException);
            }
            throw new ReservationServiceUnavailableException(
                    "reservation-service returned " + responseException.getStatusCode(), responseException);
        } catch (RestClientException connectionFailure) {
            throw new ReservationServiceUnavailableException(
                    "Could not reach reservation-service", connectionFailure);
        }
    }
}
