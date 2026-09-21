package com.jogafacil.reservation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de {@code POST /reservations}.
 *
 * <p>{@code ownerId} e opcional nesta entrega porque a autenticacao sera
 * introduzida na Entrega 4; quando ausente o servico usa um dono padrao
 * configuravel. {@code matchId} relaciona a reserva a uma partida, quando houver.</p>
 */
public record ReservationCreateRequest(

        @NotBlank
        @Pattern(regexp = "^court_[0-9A-HJKMNP-TV-Z]{26}$",
                message = "must match court_<ULID>")
        String courtId,

        @NotBlank
        @Pattern(regexp = "^slot_[0-9A-HJKMNP-TV-Z]{26}$",
                message = "must match slot_<ULID>")
        String slotId,

        @Pattern(regexp = "^usr_[0-9A-HJKMNP-TV-Z]{26}$",
                message = "must match usr_<ULID>")
        String ownerId,

        @Pattern(regexp = "^match_[0-9A-HJKMNP-TV-Z]{26}$",
                message = "must match match_<ULID>")
        String matchId) {
}
