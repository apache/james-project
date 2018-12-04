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
package org.apache.james.backends.cassandra.components;

import static org.apache.james.backends.cassandra.components.CassandraTable.InitializationStatus.ALREADY_DONE;
import static org.apache.james.backends.cassandra.components.CassandraTable.InitializationStatus.FULL;
import static org.apache.james.backends.cassandra.components.CassandraTable.InitializationStatus.PARTIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.apache.james.backends.cassandra.components.CassandraTable.InitializationStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraTableTest {
    private static final String NAME = "tableName";
    private static final Statement STATEMENT = SchemaBuilder.createTable(NAME);
    private static final CassandraTable TABLE = new CassandraTable(NAME, STATEMENT);

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraTable.class)
                .verify();
    }

    @Test
    void initializeShouldExecuteCreateStatementAndReturnFullWhenTableDoesNotExist() {
        KeyspaceMetadata keyspace = mock(KeyspaceMetadata.class);
        when(keyspace.getTable(NAME)).thenReturn(null);
        Session session = mock(Session.class);

        assertThat(TABLE.initialize(keyspace, session))
                .isEqualByComparingTo(FULL);

        verify(keyspace).getTable(NAME);
        verify(session).execute(STATEMENT);
    }

    @Test
    void initializeShouldExecuteReturnAlreadyDoneWhenTableExists() {
        KeyspaceMetadata keyspace = mock(KeyspaceMetadata.class);
        when(keyspace.getTable(NAME)).thenReturn(mock(TableMetadata.class));
        Session session = mock(Session.class);

        assertThat(TABLE.initialize(keyspace, session))
                .isEqualByComparingTo(ALREADY_DONE);

        verify(keyspace).getTable(NAME);
        verify(session, never()).execute(STATEMENT);
    }

    @ParameterizedTest
    @MethodSource
    void initializationStatusReduceShouldFallIntoTheRightState(InitializationStatus left, InitializationStatus right, InitializationStatus expectedResult) {
        assertThat(left.reduce(right)).isEqualByComparingTo(expectedResult);
    }

    private static Stream<Arguments> initializationStatusReduceShouldFallIntoTheRightState() {
        return Stream.of(
                Arguments.of(ALREADY_DONE, ALREADY_DONE, ALREADY_DONE),
                Arguments.of(ALREADY_DONE, PARTIAL, PARTIAL),
                Arguments.of(ALREADY_DONE, FULL, PARTIAL),
                Arguments.of(PARTIAL, PARTIAL, PARTIAL),
                Arguments.of(PARTIAL, PARTIAL, PARTIAL),
                Arguments.of(PARTIAL, FULL, PARTIAL),
                Arguments.of(FULL, ALREADY_DONE, PARTIAL),
                Arguments.of(FULL, PARTIAL, PARTIAL),
                Arguments.of(FULL, FULL, FULL)
        );
    }
}