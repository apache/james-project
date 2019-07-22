package org.apache.james.backends.cassandra.versions;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class SchemaTransitionTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(SchemaTransition.class)
                .verify();
    }


}