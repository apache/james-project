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

package org.apache.james.rrt.cassandra.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Domain;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.CassandraRRTDataDefinition;
import org.apache.james.rrt.cassandra.CassandraRecipientRewriteTableDAO;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.task.Task;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MappingsSourcesMigrationTest {
    private static final int THREAD_COUNT = 10;
    private static final int OPERATION_COUNT = 10;
    private static final int MAPPING_COUNT = 100;

    private static final String USER = "test";
    private static final String ADDRESS = "test@domain";
    private static final MappingSource SOURCE = MappingSource.fromUser(USER, Domain.LOCALHOST);
    private static final Mapping MAPPING = Mapping.alias(ADDRESS);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRRTDataDefinition.MODULE);

    private CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO;
    private CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;

    private MappingsSourcesMigration migration;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraRecipientRewriteTableDAO = new CassandraRecipientRewriteTableDAO(cassandra.getConf());
        cassandraMappingsSourcesDAO = new CassandraMappingsSourcesDAO(cassandra.getConf());

        migration = new MappingsSourcesMigration(cassandraRecipientRewriteTableDAO, cassandraMappingsSourcesDAO);
    }

    @Test
    void emptyMigrationShouldSucceed() throws InterruptedException {
        assertThat(migration.asTask().run()).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void migrationShouldSucceedWithData() throws InterruptedException {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void migrationShouldCreateMappingSourceFromMapping() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        migration.apply();

        assertThat(cassandraMappingsSourcesDAO.retrieveSources(MAPPING).collectList().block())
            .containsExactly(SOURCE);

        assertThat(migration.createAdditionalInformation().getSuccessfulMappingsCount()).isEqualTo(1);
        assertThat(migration.createAdditionalInformation().getErrorMappingsCount()).isEqualTo(0);
    }

    @Test
    void migrationShouldCreateMultipleMappingSourcesFromMappings() {
        MappingSource source2 = MappingSource.fromUser("bob", Domain.LOCALHOST);

        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();
        cassandraRecipientRewriteTableDAO.addMapping(source2, MAPPING).block();

        migration.apply();

        assertThat(cassandraMappingsSourcesDAO.retrieveSources(MAPPING).collectList().block())
            .containsOnly(SOURCE, source2);

        assertThat(migration.createAdditionalInformation().getSuccessfulMappingsCount()).isEqualTo(2);
        assertThat(migration.createAdditionalInformation().getErrorMappingsCount()).isEqualTo(0);
    }

    @Test
    void migrationShouldReturnPartialWhenGetAllMappingsFromMappingsFail() throws InterruptedException {
        CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO = mock(CassandraRecipientRewriteTableDAO.class);
        CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO = mock(CassandraMappingsSourcesDAO.class);
        migration = new MappingsSourcesMigration(cassandraRecipientRewriteTableDAO, cassandraMappingsSourcesDAO);

        when(cassandraRecipientRewriteTableDAO.getAllMappings()).thenReturn(Flux.error(new RuntimeException()));

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
        assertThat(migration.createAdditionalInformation().getSuccessfulMappingsCount()).isEqualTo(0);
        assertThat(migration.createAdditionalInformation().getErrorMappingsCount()).isEqualTo(0);
    }

    @Test
    void migrationShouldReturnPartialAddMappingFails() throws InterruptedException {
        CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO = mock(CassandraRecipientRewriteTableDAO.class);
        CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO = mock(CassandraMappingsSourcesDAO.class);
        migration = new MappingsSourcesMigration(cassandraRecipientRewriteTableDAO, cassandraMappingsSourcesDAO);

        when(cassandraRecipientRewriteTableDAO.getAllMappings())
            .thenReturn(Flux.just(Pair.of(SOURCE, MAPPING)));
        when(cassandraMappingsSourcesDAO.addMapping(any(Mapping.class), any(MappingSource.class)))
            .thenReturn(Mono.error(new RuntimeException()));

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
        assertThat(migration.createAdditionalInformation().getSuccessfulMappingsCount()).isEqualTo(0);
        assertThat(migration.createAdditionalInformation().getErrorMappingsCount()).isEqualTo(1);
    }

    @Test
    void migrationShouldHaveCorrectErrorCountWhenMultipleAddMappingFails() throws InterruptedException {
        MappingSource source2 = MappingSource.fromUser("bob", Domain.LOCALHOST);

        CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO = mock(CassandraRecipientRewriteTableDAO.class);
        CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO = mock(CassandraMappingsSourcesDAO.class);
        migration = new MappingsSourcesMigration(cassandraRecipientRewriteTableDAO, cassandraMappingsSourcesDAO);

        when(cassandraRecipientRewriteTableDAO.getAllMappings())
            .thenReturn(Flux.just(
                Pair.of(SOURCE, MAPPING),
                Pair.of(source2, MAPPING)));
        when(cassandraMappingsSourcesDAO.addMapping(any(Mapping.class), any(MappingSource.class)))
            .thenReturn(Mono.error(new RuntimeException()));

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
        assertThat(migration.createAdditionalInformation().getSuccessfulMappingsCount()).isEqualTo(0);
        assertThat(migration.createAdditionalInformation().getErrorMappingsCount()).isEqualTo(2);
    }

    @Test
    void migrationShouldBeIdempotentWhenRunMultipleTimes() throws ExecutionException, InterruptedException {
        IntStream.range(0, MAPPING_COUNT)
            .forEach(i -> cassandraRecipientRewriteTableDAO
                .addMapping(MappingSource.parse("source" + i + "@domain"), MAPPING).block());

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> migration.apply())
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(cassandraMappingsSourcesDAO.retrieveSources(MAPPING).collectList().block())
            .hasSize(MAPPING_COUNT);
    }
}
