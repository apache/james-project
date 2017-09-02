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

package org.apache.james.webadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.mailbox.cassandra.mail.migration.Migration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.datastax.driver.core.Session;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

public class CassandraMigrationServiceTest {
    private static final int LATEST_VERSION = 3;
    private static final int CURRENT_VERSION = 2;
    private static final int OLDER_VERSION = 1;
    private CassandraMigrationService testee;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private ExecutorService executorService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private Migration successfulMigration;

    @Before
    public void setUp() throws Exception {
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
        successfulMigration = mock(Migration.class);
        when(successfulMigration.run()).thenReturn(Migration.MigrationResult.COMPLETED);
        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(CURRENT_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
        executorService = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void getCurrentVersionShouldReturnCurrentVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

        assertThat(testee.getCurrentVersion().getversion().get()).isEqualTo(CURRENT_VERSION);
    }

    @Test
    public void getLatestVersionShouldReturnTheLatestVersion() throws Exception {
        assertThat(testee.getLatestVersion().getversion().get()).isEqualTo(LATEST_VERSION);
    }

    @Test
    public void upgradeToVersionShouldThrowWhenCurrentVersionIsUpToDate() throws Exception {
        expectedException.expect(IllegalStateException.class);

        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(CURRENT_VERSION)));

        testee.upgradeToVersion(OLDER_VERSION);
    }

    @Test
    public void upgradeToVersionShouldUpdateToVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        testee.upgradeToVersion(CURRENT_VERSION);

        verify(schemaVersionDAO, times(1)).updateVersion(eq(CURRENT_VERSION));
    }

    @Test
    public void upgradeToLastVersionShouldThrowWhenVersionIsUpToDate() throws Exception {
        expectedException.expect(IllegalStateException.class);

        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(LATEST_VERSION)));

        testee.upgradeToLastVersion();
    }

    @Test
    public void upgradeToLastVersionShouldUpdateToLatestVersion() throws Exception {
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        testee.upgradeToLastVersion();

        verify(schemaVersionDAO, times(1)).updateVersion(eq(LATEST_VERSION));
    }

    @Test
    public void upgradeToVersionShouldThrowOnMissingVersion() throws Exception {
        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, successfulMigration)
            .put(LATEST_VERSION, successfulMigration)
            .build();
        testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
        when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

        expectedException.expect(NotImplementedException.class);

        testee.upgradeToVersion(LATEST_VERSION);
    }

    @Test
    public void upgradeToVersionShouldUpdateIntermediarySuccessfulMigrationsInCaseOfError() throws Exception {
        try {
            Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
                .put(OLDER_VERSION, successfulMigration)
                .put(LATEST_VERSION, successfulMigration)
                .build();
            testee = new CassandraMigrationService(schemaVersionDAO, allMigrationClazz, LATEST_VERSION);
            when(schemaVersionDAO.getCurrentSchemaVersion()).thenReturn(CompletableFuture.completedFuture(Optional.of(OLDER_VERSION)));

            expectedException.expect(RuntimeException.class);

            testee.upgradeToVersion(LATEST_VERSION);
        } finally {
            verify(schemaVersionDAO).updateVersion(CURRENT_VERSION);
        }
    }

    @Test
    public void concurrentMigrationsShouldFail() throws Exception {
        // Given a stateful migration service
        Migration wait1SecondMigration = mock(Migration.class);
        doAnswer(invocation -> {
            Thread.sleep(1000);
            return Migration.MigrationResult.COMPLETED;
        }).when(wait1SecondMigration).run();
        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, wait1SecondMigration)
            .put(CURRENT_VERSION, wait1SecondMigration)
            .put(LATEST_VERSION, wait1SecondMigration)
            .build();
        testee = new CassandraMigrationService(new InMemorySchemaDAO(OLDER_VERSION), allMigrationClazz, LATEST_VERSION);

        // When I perform a concurrent migration
        AtomicInteger encounteredExceptionCount = new AtomicInteger(0);
        executorService.submit(() -> testee.upgradeToVersion(LATEST_VERSION));
        executorService.submit(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }

            try {
                testee.upgradeToVersion(LATEST_VERSION);
            } catch (IllegalStateException e) {
                encounteredExceptionCount.incrementAndGet();
            }
        });
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // Then the second migration fails
        assertThat(encounteredExceptionCount.get()).isEqualTo(1);
    }

    @Test
    public void partialMigrationShouldThrow() throws Exception {
        Migration migration1 = mock(Migration.class);
        when(migration1.run()).thenReturn(Migration.MigrationResult.PARTIAL);
        Migration migration2 = successfulMigration;

        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, migration1)
            .put(CURRENT_VERSION, migration2)
            .build();
        testee = new CassandraMigrationService(new InMemorySchemaDAO(OLDER_VERSION), allMigrationClazz, LATEST_VERSION);

        expectedException.expect(MigrationException.class);

        testee.upgradeToVersion(LATEST_VERSION);
    }

    @Test
    public void partialMigrationShouldAbortMigrations() throws Exception {
        Migration migration1 = mock(Migration.class);
        when(migration1.run()).thenReturn(Migration.MigrationResult.PARTIAL);
        Migration migration2 = mock(Migration.class);
        when(migration2.run()).thenReturn(Migration.MigrationResult.COMPLETED);

        Map<Integer, Migration> allMigrationClazz = ImmutableMap.<Integer, Migration>builder()
            .put(OLDER_VERSION, migration1)
            .put(CURRENT_VERSION, migration2)
            .build();
        testee = new CassandraMigrationService(new InMemorySchemaDAO(OLDER_VERSION), allMigrationClazz, LATEST_VERSION);

        expectedException.expect(MigrationException.class);

        try {
            testee.upgradeToVersion(LATEST_VERSION);
        } finally {
            verify(migration1, times(1)).run();
            verifyNoMoreInteractions(migration1);
            verifyZeroInteractions(migration2);
        }
    }

    public static class InMemorySchemaDAO extends CassandraSchemaVersionDAO {
        private int currentVersion;

        public InMemorySchemaDAO(int currentVersion) {
            super(mock(Session.class), null);
            this.currentVersion = currentVersion;
        }

        @Override
        public CompletableFuture<Optional<Integer>> getCurrentSchemaVersion() {
            return CompletableFuture.completedFuture(Optional.of(currentVersion));
        }

        @Override
        public CompletableFuture<Void> updateVersion(int newVersion) {
            currentVersion = newVersion;
            return CompletableFuture.completedFuture(null);
        }
    }
}