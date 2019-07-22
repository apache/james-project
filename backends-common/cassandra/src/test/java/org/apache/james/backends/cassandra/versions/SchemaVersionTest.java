package org.apache.james.backends.cassandra.versions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaVersionTest {

    static final SchemaVersion VERSION_1 = new SchemaVersion(1);
    static final SchemaVersion VERSION_2 = new SchemaVersion(2);
    static final SchemaVersion VERSION_3 = new SchemaVersion(3);
    static final SchemaVersion VERSION_4 = new SchemaVersion(4);

    @Test
    void listTransitionsForTargetShouldReturnEmptyOnSameVersion() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_2)).isEmpty();
    }

    @Test
    void listTransitionsForTargetShouldReturnEmptyOnSmallerVersion() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_1)).isEmpty();
    }

    @Test
    void listTransitionsForTargetShouldReturnListOfVersionsWhenGreater() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_4))
                .containsExactly(SchemaTransition.to(VERSION_3), SchemaTransition.to(VERSION_4));
    }

}