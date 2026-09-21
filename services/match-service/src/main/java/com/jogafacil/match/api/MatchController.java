package com.jogafacil.match.api;

import com.jogafacil.contracts.api.ApiMediaType;
import com.jogafacil.match.api.dto.CreateMatchRequest;
import com.jogafacil.match.api.dto.MatchResponse;
import com.jogafacil.match.domain.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de partidas.
 *
 * <ul>
 *   <li>{@code POST /matches} - cria a partida reservando o horario no
 *       reservation-service; aceita {@code Idempotency-Key} opcional que e
 *       propagado como chave da reserva;</li>
 *   <li>{@code GET /matches/{matchId}} - consulta a partida.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/matches", produces = {ApiMediaType.V1, ApiMediaType.JSON})
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping(consumes = {ApiMediaType.V1, ApiMediaType.JSON})
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateMatchRequest request) {
        return MatchResponse.from(matchService.create(idempotencyKey, request));
    }

    @GetMapping("/{matchId}")
    public MatchResponse get(@PathVariable String matchId) {
        return MatchResponse.from(matchService.get(matchId));
    }
}
