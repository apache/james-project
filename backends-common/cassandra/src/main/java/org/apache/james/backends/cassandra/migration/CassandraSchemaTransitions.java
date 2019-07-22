package org.apache.james.backends.cassandra.migration;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.versions.SchemaTransition;

import com.google.common.annotations.VisibleForTesting;

public class CassandraSchemaTransitions {

    private final Map<SchemaTransition, Migration> transitions;

    @Inject
    @VisibleForTesting
    public CassandraSchemaTransitions(Map<SchemaTransition, Migration> transitions) {
        this.transitions = transitions;
    }

    public Optional<Migration> findMigration(SchemaTransition transition) {
        return Optional.ofNullable(transitions.get(transition));
    }
}
