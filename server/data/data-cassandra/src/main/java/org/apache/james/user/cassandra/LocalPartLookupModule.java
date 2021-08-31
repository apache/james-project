package org.apache.james.user.cassandra;

import static com.datastax.driver.core.DataType.text;

import org.apache.james.backends.cassandra.components.CassandraModule;

public interface LocalPartLookupModule {
    interface Table {
        String TABLE_NAME = "localpart";

        String LOCALPART = "localpart";
        String USERNAME = "username";
    }

    CassandraModule MODULE = CassandraModule.builder()
        .table(Table.TABLE_NAME)
        .comment("Allow to retrieve users associated to a given localpart, homonyms being dis-ambiguated using the caller IP")
        .statement(schema -> schema.addPartitionKey(Table.LOCALPART, text())
            .addClusteringColumn(Table.USERNAME, text()))
        .build();
}
