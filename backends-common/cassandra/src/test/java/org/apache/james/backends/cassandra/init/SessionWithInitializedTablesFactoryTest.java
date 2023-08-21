/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.cassandra.init;

import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.MAX_VERSION;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.MIN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

@ExtendWith(DockerCassandraExtension.class)
class SessionWithInitializedTablesFactoryTest {

    public static final KeyspaceConfiguration KEYSPACE_CONFIGURATION = KeyspaceConfiguration.builder()
        .keyspace("abcd")
        .replicationFactor(1)
        .disableDurableWrites();

    private static final String TABLE_NAME = "tablename";
    private static final String TYPE_NAME = "typename";
    private static final String PROPERTY = "property";

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraModule.table(TABLE_NAME)
                    .comment("Testing table")
                    .statement(statement -> types -> statement
                            .withPartitionKey("id", DataTypes.TIMEUUID)
                            .withClusteringColumn("clustering", DataTypes.BIGINT))
                    .build(),
            CassandraModule.type(TYPE_NAME)
                    .statement(statement -> statement.withField(PROPERTY, DataTypes.TEXT))
                    .build());

    private Supplier<CqlSession> testee;

    @BeforeEach
    void setUp(DockerCassandraExtension.DockerCassandra cassandraServer) {
        this.testee = createSession(cassandraServer);
    }

    @AfterEach
    void tearDown() {
        cleanCassandra(testee.get());
    }

    @BeforeAll
    @AfterAll
    static void stabilizeCassandra(DockerCassandraExtension.DockerCassandra cassandraServer) {
        cleanCassandra(createSession(cassandraServer).get());
    }

    @Test
    void createSessionShouldSetTheLatestSchemaVersionWhenCreatingTypesAndTables() {
        assertThat(versionManager(testee.get()).computeVersion().block())
                .isEqualTo(MAX_VERSION);
    }

    @Test
    void createSessionShouldKeepTheSetSchemaVersionWhenTypesAndTablesHaveNotChanged() {
        CqlSession session = testee.get();
        assertThat(versionManager(session).computeVersion().block())
                .isEqualTo(MAX_VERSION);

        new CassandraTableManager(MODULE, session).clearTables(t -> true);
        versionManagerDAO(session).updateVersion(MIN_VERSION).block();
        assertThat(versionManager(session).computeVersion().block())
                .isEqualTo(MIN_VERSION);

        assertThat(versionManager(testee.get()).computeVersion().block())
                .isEqualTo(MIN_VERSION);
    }

    @Test
    void createSessionShouldKeepTheSetSchemaVersionWhenTypesAndTablesHavePartiallyChanged() {
        CqlSession session = testee.get();
        assertThat(versionManager(session).computeVersion().block())
                .isEqualTo(MAX_VERSION);

        new CassandraTableManager(MODULE, session).clearTables(t -> true);
        versionManagerDAO(session).updateVersion(MIN_VERSION).block();
        assertThat(versionManager(session).computeVersion().block())
                .isEqualTo(MIN_VERSION);
        session.execute(SchemaBuilder.dropTable(TABLE_NAME).build());
        session.execute(SchemaBuilder.dropType(TYPE_NAME).build());

        assertThat(versionManager(testee.get()).computeVersion().block())
                .isEqualTo(MIN_VERSION);
    }

    private static Supplier<CqlSession> createSession(DockerCassandraExtension.DockerCassandra cassandraServer) {
        ClusterConfiguration clusterConfiguration = DockerCassandra.configurationBuilder(cassandraServer.getHost())
            .build();
        KeyspaceConfiguration keyspaceConfiguration = DockerCassandra.mainKeyspaceConfiguration();
        CqlSession cluster = ClusterFactory.create(clusterConfiguration, keyspaceConfiguration);
        KeyspaceFactory.createKeyspace(keyspaceConfiguration, cluster).block();

        return () -> new SessionWithInitializedTablesFactory(cluster, MODULE).get();
    }

    private static void cleanCassandra(CqlSession session) {
        MODULE.moduleTables().forEach(table -> session.execute(SchemaBuilder.dropTable(table.getName()).build()));
        MODULE.moduleTypes().forEach(type -> session.execute(SchemaBuilder.dropType(type.getName()).build()));
    }

    private CassandraSchemaVersionManager versionManager(CqlSession session) {
        return new CassandraSchemaVersionManager(versionManagerDAO(session));
    }

    private CassandraSchemaVersionDAO versionManagerDAO(CqlSession session) {
        return new CassandraSchemaVersionDAO(session);
    }
}