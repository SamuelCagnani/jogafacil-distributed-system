package com.jogafacil.contracts.id;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identificador de recurso no formato {@code <prefixo>_<ULID>}.
 *
 * <p>O prefixo indica o tipo do recurso (por exemplo {@code res_} para reserva e
 * {@code match_} para partida) e o ULID de 26 caracteres garante unicidade global
 * e ordenacao lexicografica. Exemplo: {@code res_01ARZ3NDEKTSV4RRFFQ69G5FAV}.</p>
 */
public record ResourceId(String value) {

    /** Prefixo de tipo seguido de ULID em Crockford Base32 (sem I, L, O e U). */
    private static final Pattern FORMAT = Pattern.compile("^[a-z]+_[0-9A-HJKMNP-TV-Z]{26}$");

    public ResourceId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid resource id: " + value);
        }
    }

    public static ResourceId of(String value) {
        return new ResourceId(value);
    }

    private static boolean isValid(String value) {
        return Objects.nonNull(value) && FORMAT.matcher(value).matches();
    }
}
