package com.jogafacil.match.client;

import com.jogafacil.contracts.api.ApiMediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifica o contrato de comunicacao do match-service com o reservation-service:
 * metodo, caminho, header {@code Idempotency-Key}, media type versionado e o
 * mapeamento das respostas HTTP para excecoes do dominio.
 */
class ReservationClientTest {

    private static final String BASE_URL = "http://reservation-service:8081";
    private static final MediaType V1 = MediaType.parseMediaType(ApiMediaType.V1);

    private MockRestServiceServer server;
    private ReservationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ReservationClient(builder.build());
    }

    @Test
    void postsToReservationsWithIdempotencyKeyAndParsesTheReservationId() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "match-hold:abc"))
                .andExpect(content().contentType(V1))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(V1)
                        .body("""
                                {"reservationId":"res_01ARZ3NDEKTSV4RRFFQ69G5FAV","status":"ACTIVE"}
                                """));

        ReservedSlot slot = client.reserve("match-hold:abc", new ReservationRequest(
                "court_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "slot_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "match_01ARZ3NDEKTSV4RRFFQ69G5FAV"));

        assertThat(slot.reservationId()).isEqualTo("res_01ARZ3NDEKTSV4RRFFQ69G5FAV");
    }

    @Test
    void mapsAConflictResponseToReservationConflictException() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(V1)
                        .body("""
                                {"code":"RESERVATION_CONFLICT","status":409}
                                """));

        assertThatThrownBy(() -> client.reserve("match-hold:abc", new ReservationRequest(
                "court_01ARZ3NDEKTSV4RRFFQ69G5FAV", "slot_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV", "match_01ARZ3NDEKTSV4RRFFQ69G5FAV")))
                .isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void mapsAnUnavailableServiceToReservationUnavailableException() {
        server.expect(requestTo(BASE_URL + "/reservations"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.reserve("match-hold:abc", new ReservationRequest(
                "court_01ARZ3NDEKTSV4RRFFQ69G5FAV", "slot_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV", "match_01ARZ3NDEKTSV4RRFFQ69G5FAV")))
                .isInstanceOf(ReservationServiceUnavailableException.class);
    }
}
