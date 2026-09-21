package com.jogafacil.contracts.error;

/**
 * Catalogo de codigos de erro de dominio usados no corpo RFC 9457
 * ({@code application/problem+json}) devolvido pelos servicos.
 *
 * <p>Os codigos de conflito ({@link #RESERVATION_CONFLICT}, {@link #MATCH_FULL},
 * {@link #MATCH_NOT_OPEN}) sao o contrato comum para operacoes concorrentes sobre
 * recursos compartilhados.</p>
 */
public enum ProblemCode {

    /** Corpo da requisicao invalido. */
    VALIDATION_ERROR,

    /** Requisicao sem credenciais validas. */
    UNAUTHORIZED,

    /** Credenciais validas porem sem permissao para a operacao. */
    FORBIDDEN,

    /** Recurso inexistente. */
    NOT_FOUND,

    /** O horario da quadra ja esta reservado. */
    RESERVATION_CONFLICT,

    /** A partida ja atingiu o numero maximo de participantes. */
    MATCH_FULL,

    /** A partida nao esta aberta para novos participantes. */
    MATCH_NOT_OPEN,

    /** A Idempotency-Key foi reutilizada com um corpo diferente. */
    IDEMPOTENCY_KEY_REUSED,

    /** Falha inesperada nao classificada. */
    INTERNAL_ERROR
}
