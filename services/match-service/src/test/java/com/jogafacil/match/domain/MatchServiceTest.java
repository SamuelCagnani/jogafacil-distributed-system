package com.jogafacil.match.domain;

import com.jogafacil.contracts.error.ApiException;
import com.jogafacil.contracts.error.ProblemCode;
import com.jogafacil.match.api.dto.CreateMatchRequest;
import com.jogafacil.match.client.ReservationClient;
import com.jogafacil.match.client.ReservationConflictException;
import com.jogafacil.match.client.ReservationRequest;
import com.jogafacil.match.client.ReservationServiceUnavailableException;
import com.jogafacil.match.client.ReservedSlot;
import com.jogafacil.match.persistence.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa a orquestracao da criacao de partida: primeiro segura o horario via
 * REST no reservation-service e so entao persiste a partida.
 */
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final String OPERATION_KEY = "match-hold:op-1234567890abcdef";

    @Mock
    private ReservationClient reservationClient;
    @Mock
    private MatchRepository matchRepository;

    private MatchService matchService;
    private CreateMatchRequest request;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(reservationClient, matchRepository);
        request = new CreateMatchRequest(
                "court_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "slot_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV",
                10);
    }

    @Test
    void reservesTheSlotBeforePersistingTheMatch() {
        when(reservationClient.reserve(anyString(), any(ReservationRequest.class)))
                .thenReturn(new ReservedSlot("res_01ARZ3NDEKTSV4RRFFQ69G5FAV", "ACTIVE"));

        Match match = matchService.create(OPERATION_KEY, request);

        assertThat(match.courtId()).isEqualTo(request.courtId());
        assertThat(match.reservationId()).isEqualTo("res_01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(match.status()).isEqualTo(MatchStatus.OPEN);

        InOrder order = inOrder(reservationClient, matchRepository);
        order.verify(reservationClient).reserve(anyString(), any(ReservationRequest.class));
        order.verify(matchRepository).save(any(Match.class));
    }

    @Test
    void doesNotPersistTheMatchWhenTheSlotIsAlreadyReserved() {
        when(reservationClient.reserve(anyString(), any(ReservationRequest.class)))
                .thenThrow(new ReservationConflictException("taken"));

        assertThatThrownBy(() -> matchService.create(OPERATION_KEY, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ProblemCode.RESERVATION_CONFLICT);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void reportsServiceUnavailableWhenTheReservationServiceIsDown() {
        when(reservationClient.reserve(anyString(), any(ReservationRequest.class)))
                .thenThrow(new ReservationServiceUnavailableException("down"));

        assertThatThrownBy(() -> matchService.create(OPERATION_KEY, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(503);

        verify(matchRepository, never()).save(any());
    }
}
