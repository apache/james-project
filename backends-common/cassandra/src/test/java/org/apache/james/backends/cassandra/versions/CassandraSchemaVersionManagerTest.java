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

import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.TOO_OLD;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.TOO_RECENT;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.UPGRADABLE;
import static org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState.UP_TO_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager.SchemaState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CassandraSchemaVersionManagerTest {

    private final SchemaVersion minVersion = new SchemaVersion(2);
    private final SchemaVersion maxVersion = new SchemaVersion(4);
    private CassandraSchemaVersionDAO schemaVersionDAO;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        schemaVersionDAO = mock(CassandraSchemaVersionDAO.class);
    }

    @Test
    public void computeSchemaStateShouldReturnTooOldWhenVersionIsLessThanMinVersion() {
        SchemaVersion currentVersion = minVersion.previous();

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        assertThat(testee.computeSchemaState()).isEqualTo(TOO_OLD);
    }

    @Test
    public void computeSchemaStateShouldReturnTooOldWhenVersionIsMoreThanMaxVersion() {
        SchemaVersion currentVersion = maxVersion.next();

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        assertThat(testee.computeSchemaState()).isEqualTo(TOO_RECENT);
    }

    @Test
    public void computeSchemaStateShouldReturnUpToDateWhenVersionEqualsMaxVersion() {
        SchemaVersion currentVersion = maxVersion;

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        assertThat(testee.computeSchemaState()).isEqualTo(UP_TO_DATE);
    }

    @Test
    public void computeSchemaStateShouldReturnUpgradableWhenVersionBetweenMinAnd() {
        SchemaVersion currentVersion = minVersion.next();

        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        assertThat(testee.computeSchemaState()).isEqualTo(UPGRADABLE);
    }

    @Test
    public void constructorShouldThrowMinVersionIsSuperiorToMaxVersion() {
        expectedException.expect(IllegalArgumentException.class);

        SchemaVersion minVersion = new SchemaVersion(4);
        SchemaVersion maxVersion = new SchemaVersion(2);
        new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);
    }

    @Test
    public void computeSchemaStateShouldReturnUpToDateWhenMinMaxAndVersionEquals() {
        SchemaVersion minVersion = new SchemaVersion(4);
        SchemaVersion maxVersion = new SchemaVersion(4);
        SchemaVersion currentVersion = new SchemaVersion(4);
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(currentVersion)));

        CassandraSchemaVersionManager testee = new CassandraSchemaVersionManager(
            schemaVersionDAO,
            minVersion,
            maxVersion);

        assertThat(testee.computeSchemaState()).isEqualTo(UP_TO_DATE);
    }

    @Test
    public void defaultComputedSchemaShouldNotBeTooOldNeitherTooRecent() {
        when(schemaVersionDAO.getCurrentSchemaVersion())
            .thenReturn(CompletableFuture.completedFuture(Optional.of(CassandraSchemaVersionManager.DEFAULT_VERSION)));

        SchemaState schemaState = new CassandraSchemaVersionManager(schemaVersionDAO).computeSchemaState();

        assertThat(schemaState)
            .isNotEqualTo(TOO_RECENT)
            .isNotEqualTo(TOO_OLD);
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

        assertThat(testee.getMaximumSupportedVersion()).isEqualTo(CassandraSchemaVersionManager.MAX_VERSION);
    }
}