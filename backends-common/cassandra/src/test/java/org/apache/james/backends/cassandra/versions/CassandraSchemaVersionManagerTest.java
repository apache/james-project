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

package org.apache.james.backends.cassandra.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.lidalia.slf4jtest.LoggingEvent.info;
import static uk.org.lidalia.slf4jtest.LoggingEvent.warn;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

public class CassandraSchemaVersionManagerTest {

    private final int minVersion = 2;
    private final int maxVersion = 4;
    private CassandraSchemaVersionDAO schemaVersionDAO;
    private TestLogger logger = TestLoggerFactory.getTestLogger(CassandraSchemaVersionManager.class);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
    }

    @After
    public void tearDown() {
        TestLoggerFactory.clear();
    }

    @Test
    public void ensureSchemaIsSupportedShouldThrowIfSchemaVersionIsTooOld() {
        expectedException.expect(IllegalStateException.class);

        int currentVersion = minVersion - 1;

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        testee.ensureSchemaIsSupported();
    }

    @Test
    public void ensureSchemaIsSupportedShouldThrowIfSchemaVersionIsTooRecent() {
        expectedException.expect(IllegalStateException.class);

        int currentVersion = maxVersion + 1;

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        testee.ensureSchemaIsSupported();
    }

    @Test
    public void ensureSchemaIsSupportedShouldLogOkIfSchemaIsUpToDate() {
        int currentVersion = maxVersion;

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        testee.ensureSchemaIsSupported();

        assertThat(logger.getLoggingEvents()).containsExactly(info("Schema version is up-to-date"));
    }

    @Test
    public void ensureSchemaIsSupportedShouldLogAsWarningIfSchemaIsSupportedButNotUpToDate() {
        int currentVersion = maxVersion - 1;

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        testee.ensureSchemaIsSupported();

        assertThat(logger.getLoggingEvents())
            .containsExactly(warn("Current schema version is %d. Recommended version is %d",
                currentVersion,
                maxVersion));
    }

    @Test
    public void constructorShouldThrowOnNegativeMinVersion() {
        expectedException.expect(IllegalArgumentException.class);

        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            -1,
            maxVersion);
    }

    @Test
    public void constructorShouldThrowOnZeroMinVersion() {
        expectedException.expect(IllegalArgumentException.class);

        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            0,
            maxVersion);
    }

    @Test
    public void constructorShouldThrowOnNegativeMaxVersion() {
        expectedException.expect(IllegalArgumentException.class);

        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            -1);
    }

    @Test
    public void constructorShouldThrowOnZeroMaxVersion() {
        expectedException.expect(IllegalArgumentException.class);

        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            0);
    }

    @Test
    public void constructorShouldThrowMinVersionIsSuperiorToMaxVersion() {
        expectedException.expect(IllegalArgumentException.class);

        int minVersion = 4;
        int maxVersion = 2;
        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);
    }

    @Test
    public void ensureSchemaIsSupportedShouldActAsUpToDateWhenMinMaxAndCurrentVersionsAreTheSame() {
        int minVersion = 4;
        int maxVersion = 4;
        int currentVersion = 4;
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        testee.ensureSchemaIsSupported();

        assertThat(logger.getLoggingEvents()).containsExactly(info("Schema version is up-to-date"));
    }

    @Test
    public void ensureSchemaIsSupportedShouldNotThrowOnNewCassandra() {
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(CassandraSchemaVersionManager.DEFAULT_VERSION)));

        new CassandraSchemaVersionManager(schemaVersionDAO).ensureSchemaIsSupported();
    }

    @Test
    public void ensureSchemaIsSupportedShouldNotThrowButLogWhenNoVersionNumberFoundOnCassandra() {
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        new CassandraSchemaVersionManager(schemaVersionDAO).ensureSchemaIsSupported();

        assertThat(logger.getAllLoggingEvents())
            .contains(warn("No schema version information found on Cassandra, we assume schema is at version {}",
                CassandraSchemaVersionManager.DEFAULT_VERSION));
    }

    @Test
    public void ensureSchemaDefaultConstructorUseCorrectMinVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(schemaVersionDAO);

        assertThat(testee.getMinimumSupportedVersion()).isEqualTo(CassandraSchemaVersionManager.MIN_VERSION);
    }

    @Test
    public void ensureSchemaDefaultConstructorUseCorrectMaxVersion() {
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(schemaVersionDAO);

        assertThat(testee.getMinimumSupportedVersion()).isEqualTo(CassandraSchemaVersionManager.MAX_VERSION);
    }
}