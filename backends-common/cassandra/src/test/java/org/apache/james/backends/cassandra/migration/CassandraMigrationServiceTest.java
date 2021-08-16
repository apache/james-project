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

package org.apache.james.backends.cassandra.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.task.Task;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

class CassandraMigrationServiceTest {
    private static final SchemaVersion LATEST_VERSION = new SchemaVersion(3);
    private static final SchemaVersion INTERMEDIARY_VERSION = new SchemaVersion(2);
    private static final SchemaVersion CURRENT_VERSION = INTERMEDIARY_VERSION;
    private static final SchemaVersion OLDER_VERSION = new SchemaVersion(1);
    private static final SchemaTransition FROM_OLDER_TO_CURRENT = SchemaTransition.to(CURRENT_VERSION);
    private static final SchemaTransition FROM_CURRENT_TO_LATEST = SchemaTransition.to(LATEST_VERSION);
    private CassandraMigrationService testee;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private ExecutorService executorService;

    private Migration successfulMigration;

    @BeforeEach
    void setUp() throws Exception {
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.empty()));
        when(schemaVersionDAO.updateVersion(any())).thenReturn(Mono.empty());

        Task successFulTask = mock(Task.class);
        when(successFulTask.run()).thenReturn(Task.Result.COMPLETED);
        successfulMigration = mock(Migration.class);
        when(successfulMigration.asTask()).thenReturn(successFulTask);
        CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(
            FROM_OLDER_TO_CURRENT, successfulMigration,
            FROM_CURRENT_TO_LATEST, successfulMigration));
        testee = new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION);
        executorService = Executors.newFixedThreadPool(2,
            NamedThreadFactory.withClassName(getClass()));
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void getCurrentVersionShouldReturnCurrentVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        assertThat(testee.getCurrentVersion()).contains(CURRENT_VERSION);
    }

    @Test
    void getLatestVersionShouldReturnTheLatestVersion() {
        assertThat(testee.getLatestVersion()).contains(LATEST_VERSION);
    }

    @Test
    void upgradeToVersionShouldNotThrowWhenCurrentVersionIsUpToDate() throws InterruptedException {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        assertThat(testee.upgradeToVersion(OLDER_VERSION).run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void upgradeToVersionShouldUpdateToVersion() throws InterruptedException {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        testee.upgradeToVersion(CURRENT_VERSION).run();

        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
    }

    @Test
    void upgradeToLastVersionShouldNotThrowWhenVersionIsUpToDate() throws InterruptedException {

        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(LATEST_VERSION)));

        assertThat(testee.upgradeToLastVersion().run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void upgradeToLastVersionShouldUpdateToLatestVersion() throws InterruptedException {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        testee.upgradeToLastVersion().run();

        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
    }

    @Test
    void upgradeToVersionShouldThrowOnMissingVersion() throws InterruptedException {
        CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(FROM_OLDER_TO_CURRENT, successfulMigration));

        testee = new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION);
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        assertThatThrownBy(() -> testee.upgradeToVersion(LATEST_VERSION).run())
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    void upgradeToVersionShouldUpdateIntermediarySuccessfulMigrationsInCaseOfError() throws InterruptedException {
        try {
            CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(
                FROM_OLDER_TO_CURRENT, successfulMigration,
                FROM_CURRENT_TO_LATEST, () -> {
                    throw new RuntimeException();
                }));

            testee = new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION);
            when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

            assertThatThrownBy(() -> testee.upgradeToVersion(LATEST_VERSION).run())
                .isInstanceOf(RuntimeException.class);
        } finally {
            verify(schemaVersionDAO).updateVersion(CURRENT_VERSION);
        }
    }

    @Test
    void partialMigrationShouldThrow() throws InterruptedException {
        InMemorySchemaDAO schemaVersionDAO = new InMemorySchemaDAO(OLDER_VERSION);
        Task failingTask = mock(Task.class);
        when(failingTask.run()).thenThrow(MigrationException.class);
        Migration migration1 = failingTask::run;
        Migration migration2 = successfulMigration;

        CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(
            FROM_OLDER_TO_CURRENT, migration1,
            FROM_CURRENT_TO_LATEST, migration2));

        testee = new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION);

        assertThatThrownBy(() -> testee.upgradeToVersion(LATEST_VERSION).run())
            .isInstanceOf(MigrationException.class);
    }

    @Test
    void partialMigrationShouldAbortMigrations() throws InterruptedException {
        InMemorySchemaDAO schemaVersionDAO = new InMemorySchemaDAO(OLDER_VERSION);
        Task failingTask = mock(Task.class);
        when(failingTask.run()).thenThrow(MigrationException.class);
        Migration migration1 = failingTask::run;
        Migration migration2 = mock(Migration.class);

        CassandraSchemaTransitions transitions = new CassandraSchemaTransitions(ImmutableMap.of(
                FROM_OLDER_TO_CURRENT, migration1,
                FROM_CURRENT_TO_LATEST, migration2));

        testee = new CassandraMigrationService(schemaVersionDAO, transitions, version -> new MigrationTask(schemaVersionDAO, transitions, version), LATEST_VERSION);

        assertThatThrownBy(() -> testee.upgradeToVersion(LATEST_VERSION).run())
            .isInstanceOf(MigrationException.class);

        verify(failingTask, times(1)).run();
        verifyNoMoreInteractions(failingTask);
    }

    public static class InMemorySchemaDAO extends CassandraSchemaVersionDAO {
        private SchemaVersion currentVersion;

        public InMemorySchemaDAO(SchemaVersion currentVersion) {
            super(mock(CqlSession.class));
            this.currentVersion = currentVersion;
        }

        @Override
        public Mono<Optional<SchemaVersion>> getCurrentSchemaVersion() {
            return Mono.just(Optional.of(currentVersion));
        }

        @Override
        public Mono<Void> updateVersion(SchemaVersion newVersion) {
            currentVersion = newVersion;
            return Mono.empty();
        }
    }
}