package com.jogafacil.contracts.id;

import com.github.f4b6a3.ulid.UlidFactory;

/**
 * Gera identificadores de recurso com prefixo de tipo.
 *
 * <p>Cada chamada produz um ULID monotonicamente crescente, portanto os ids sao
 * unicos e ordenaveis por tempo mesmo quando gerados por instancias diferentes
 * dos servicos, sem coordenacao central.</p>
 */
public final class UlidGenerator {

    public static final String USER_PREFIX = "usr_";
    public static final String ESTABLISHMENT_PREFIX = "est_";
    public static final String COURT_PREFIX = "court_";
    public static final String SLOT_PREFIX = "slot_";
    public static final String MATCH_PREFIX = "match_";
    public static final String PARTICIPATION_PREFIX = "part_";
    public static final String RESERVATION_PREFIX = "res_";
    public static final String EVENT_PREFIX = "evt_";

    private static final UlidFactory MONOTONIC = UlidFactory.newMonotonicInstance();

    private UlidGenerator() {
    }

    /**
     * Gera um id no formato {@code prefixo + ULID}.
     *
     * @param prefix prefixo de tipo terminado em underscore (ex.: {@code res_})
     * @throws IllegalArgumentException se o prefixo for invalido
     */
    public static String generate(String prefix) {
        if (prefix == null || !prefix.matches("^[a-z]+_$")) {
            throw new IllegalArgumentException("Invalid id prefix: " + prefix);
        }
        return prefix + MONOTONIC.create();
    }
}
