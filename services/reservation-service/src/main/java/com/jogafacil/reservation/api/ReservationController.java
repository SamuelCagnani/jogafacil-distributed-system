package com.jogafacil.reservation.api;

import com.jogafacil.contracts.api.ApiMediaType;
import com.jogafacil.reservation.api.dto.ReservationCreateRequest;
import com.jogafacil.reservation.api.dto.ReservationResponse;
import com.jogafacil.reservation.domain.ReservationResult;
import com.jogafacil.reservation.domain.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de reservas.
 *
 * <ul>
 *   <li>{@code POST /reservations} - confirma a reserva de um horario; requer
 *       {@code Idempotency-Key} e devolve 201 (ou 200 em replay);</li>
 *   <li>{@code GET /reservations/{reservationId}} - consulta a reserva;</li>
 *   <li>{@code DELETE /reservations/{reservationId}} - cancela e libera o horario
 *       de forma idempotente.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/reservations", produces = {ApiMediaType.V1, ApiMediaType.JSON})
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping(consumes = {ApiMediaType.V1, ApiMediaType.JSON})
    public ResponseEntity<ReservationResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationCreateRequest request) {
        ReservationResult result = reservationService.create(idempotencyKey, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse get(@PathVariable String reservationId) {
        return ReservationResponse.from(reservationService.get(reservationId));
    }

    @DeleteMapping("/{reservationId}")
    public ReservationResponse cancel(@PathVariable String reservationId) {
        return ReservationResponse.from(reservationService.cancel(reservationId));
    }
}
