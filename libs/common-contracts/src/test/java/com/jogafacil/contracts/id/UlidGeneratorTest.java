package com.jogafacil.contracts.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UlidGeneratorTest {

    @Test
    void generatesAnIdWithTheRequestedPrefix() {
        String id = UlidGenerator.generate(UlidGenerator.RESERVATION_PREFIX);
        assertThat(id).startsWith("res_");
        assertThat(id).matches("^[a-z]+_[0-9A-HJKMNP-TV-Z]{26}$");
    }

    @Test
    void generatedIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UlidGenerator.generate(UlidGenerator.MATCH_PREFIX));
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void generatedIdsAreLexicographicallyIncreasing() {
        String first = UlidGenerator.generate(UlidGenerator.EVENT_PREFIX);
        String second = UlidGenerator.generate(UlidGenerator.EVENT_PREFIX);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void rejectsAPrefixWithoutUnderscore() {
        assertThatThrownBy(() -> UlidGenerator.generate("res"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
