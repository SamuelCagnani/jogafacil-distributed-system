package com.jogafacil.contracts.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceIdTest {

    private static final String VALID = "res_01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @Test
    void acceptsAWellFormedPrefixedUlid() {
        ResourceId id = ResourceId.of(VALID);
        assertThat(id.value()).isEqualTo(VALID);
    }

    @Test
    void rejectsAnIdWithoutPrefix() {
        assertThatThrownBy(() -> ResourceId.of("01ARZ3NDEKTSV4RRFFQ69G5FAV"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnIdWithCharactersOutsideCrockfordBase32() {
        assertThatThrownBy(() -> ResourceId.of("res_01ARZ3NDEKTSV4RRFFQ69G5FAI"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnIdWithTheWrongLength() {
        assertThatThrownBy(() -> ResourceId.of("res_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> ResourceId.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
