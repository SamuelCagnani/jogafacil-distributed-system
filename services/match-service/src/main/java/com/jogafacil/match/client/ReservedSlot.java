package com.jogafacil.match.client;

/**
 * Resposta relevante do reservation-service: o id da reserva confirmada e seu
 * status. Os demais campos do contrato sao ignorados na desserializacao.
 */
public record ReservedSlot(String reservationId, String status) {
}
