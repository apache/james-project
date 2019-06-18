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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.task.Task;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableMap;
import reactor.core.publisher.Mono;

public class CassandraMigrationServiceTest {
    private static final SchemaVersion LATEST_VERSION = new SchemaVersion(3);
    private static final SchemaVersion INTERMEDIARY_VERSION = new SchemaVersion(2);
    private static final SchemaVersion CURRENT_VERSION = INTERMEDIARY_VERSION;
    private static final SchemaVersion OLDER_VERSION = new SchemaVersion(1);
    private CassandraMigrationService testee;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private ExecutorService executorService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private Migration successfulMigration;

    @Before
    public void setUp() throws Exception {
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
        when(schemaVersionDAO.updateVersion(any())).thenReturn(Mono.empty());

        successfulMigration = mock(Migration.class);
        when(successfulMigration.run()).thenReturn(Migration.Result.COMPLETED);
        Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(CURRENT_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
        executorService = Executors.newFixedThreadPool(2,
            NamedThreadFactory.withClassName(getClass()));
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void getCurrentVersionShouldReturnCurrentVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        assertThat(testee.getCurrentVersion()).contains(CURRENT_VERSION);
    }

    @Test
    public void getLatestVersionShouldReturnTheLatestVersion() {
        assertThat(testee.getLatestVersion()).contains(LATEST_VERSION);
    }

    @Test
    public void upgradeToVersionShouldNotThrowWhenCurrentVersionIsUpToDate() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(CURRENT_VERSION)));

        assertThat(testee.upgradeToVersion(OLDER_VERSION).run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    public void upgradeToVersionShouldUpdateToVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        testee.upgradeToVersion(CURRENT_VERSION).run();

        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
    }

    @Test
    public void upgradeToLastVersionShouldNotThrowWhenVersionIsUpToDate() {

        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(LATEST_VERSION)));

        assertThat(testee.upgradeToLastVersion().run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    public void upgradeToLastVersionShouldUpdateToLatestVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        testee.upgradeToLastVersion().run();

        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
    }

    @Test
    public void upgradeToVersionShouldThrowOnMissingVersion() {
        Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

        expectedException.expect(NotImplementedException.class);

        testee.upgradeToVersion(LATEST_VERSION).run();
    }

    @Test
    public void upgradeToVersionShouldUpdateIntermediarySuccessfulMigrationsInCaseOfError() {
        try {
            Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
                .put(OLDER_VERSION, successfulMigration)
                .put(INTERMEDIARY_VERSION, () -> Migration.Result.PARTIAL)
                .put(LATEST_VERSION, successfulMigration)
                .build();
            testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
            when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(Mono.just(Optional.of(OLDER_VERSION)));

            expectedException.expect(RuntimeException.class);

            testee.upgradeToVersion(LATEST_VERSION).run();
        } finally {
            verify(schemaVersionDAO).updateVersion(CURRENT_VERSION);
        }
    }

    @Test
    public void partialMigrationShouldThrow() {
        Migration migration1 = mock(Migration.class);
        when(migration1.run()).thenReturn(Migration.Result.PARTIAL);
        Migration migration2 = successfulMigration;

        Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
            .put(OLDER_VERSION, migration1)
            .put(CURRENT_VERSION, migration2)
            .build();
        testee = new CassandraMigrationService(new InMemorySchemaDAO(OLDER_VERSION), allMigrationClazz, LATEST_VERSION);

        expectedException.expect(MigrationException.class);

        testee.upgradeToVersion(LATEST_VERSION).run();
    }

    @Test
    public void partialMigrationShouldAbortMigrations() {
        Migration migration1 = mock(Migration.class);
        when(migration1.run()).thenReturn(Migration.Result.PARTIAL);
        Migration migration2 = mock(Migration.class);
        when(migration2.run()).thenReturn(Migration.Result.COMPLETED);

        Map<SchemaVersion, Migration> allMigrationClazz = ImmutableMap.<SchemaVersion, Migration>builder()
            .put(OLDER_VERSION, migration1)
            .put(CURRENT_VERSION, migration2)
            .build();
        testee = new CassandraMigrationService(new InMemorySchemaDAO(OLDER_VERSION), allMigrationClazz, LATEST_VERSION);

        expectedException.expect(MigrationException.class);

        try {
            testee.upgradeToVersion(LATEST_VERSION).run();
        } finally {
            verify(migration1, times(1)).run();
            verifyNoMoreInteractions(migration1);
            verifyZeroInteractions(migration2);
        }
    }

    public static class InMemorySchemaDAO extends CassandraSchemaVersionDAO {
        private SchemaVersion currentVersion;

        public InMemorySchemaDAO(SchemaVersion currentVersion) {
            super(mock(Session.class));
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