package com.jogafacil.match.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de {@code POST /matches}.
 *
 * <p>O organizador informa a quadra e o horario desejados; o match-service
 * reserva esse horario antes de criar a partida.</p>
 */
public record CreateMatchRequest(

        @NotBlank
        @Pattern(regexp = "^court_[0-9A-HJKMNP-TV-Z]{26}$", message = "must match court_<ULID>")
        String courtId,

        @NotBlank
        @Pattern(regexp = "^slot_[0-9A-HJKMNP-TV-Z]{26}$", message = "must match slot_<ULID>")
        String slotId,

        @NotBlank
        @Pattern(regexp = "^usr_[0-9A-HJKMNP-TV-Z]{26}$", message = "must match usr_<ULID>")
        String organizerId,

        @NotNull
        @Min(value = 2, message = "must be at least 2")
        @Max(value = 100, message = "must be at most 100")
        Integer maxParticipants) {
}
