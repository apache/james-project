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

import static org.apache.james.backends.cassandra.components.CassandraType.InitializationStatus.ALREADY_DONE;
import static org.apache.james.backends.cassandra.components.CassandraType.InitializationStatus.FULL;
import static org.apache.james.backends.cassandra.components.CassandraType.InitializationStatus.PARTIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.apache.james.backends.cassandra.components.CassandraType.InitializationStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateType;
import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Mono;

class CassandraTypeTest {
    private static final String NAME = "typeName";
    private static final CreateType STATEMENT = SchemaBuilder.createType(NAME)
        .withField("a", DataTypes.TEXT);
    private static final CassandraType TYPE = new CassandraType(NAME, STATEMENT);

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraType.class)
                .withPrefabValues(CreateType.class, SchemaBuilder.createType("name1")
                    .withField("a", DataTypes.TEXT),
                    SchemaBuilder.createType("name2")
                        .withField("b", DataTypes.TEXT))
                .verify();
    }

    @Test
    void initializeShouldExecuteCreateStatementAndReturnFullWhenTypeDoesNotExist() {
        KeyspaceMetadata keyspace = mock(KeyspaceMetadata.class);
        when(keyspace.getUserDefinedTypes()).thenReturn(ImmutableMap.of());
        CqlSession session = mock(CqlSession.class);
        DriverContext context = mock(DriverContext.class);
        DriverConfig config = mock(DriverConfig.class);
        when(session.getContext()).thenReturn(context);
        when(session.executeReactive(any(SimpleStatement.class))).thenReturn(new ReactiveResultSet() {
            @NotNull
            @Override
            public Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return Mono.empty();
            }

            @NotNull
            @Override
            public Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return Mono.empty();
            }

            @NotNull
            @Override
            public Publisher<Boolean> wasApplied() {
                return Mono.just(true);
            }

            @Override
            public void subscribe(Subscriber<? super ReactiveRow> s) {
                s.onComplete();
            }
        });
        when(context.getConfig()).thenReturn(config);
        when(config.getProfiles()).thenReturn(ImmutableMap.of());
        when(config.getDefaultProfile()).thenReturn(mock(DriverExecutionProfile.class));

        assertThat(TYPE.initialize(keyspace, session).block())
                .isEqualByComparingTo(FULL);

        verify(session).executeReactive(STATEMENT.build());
    }

    @Test
    void initializeShouldReturnAlreadyDoneWhenTypeExists() {
        KeyspaceMetadata keyspace = mock(KeyspaceMetadata.class);
        when(keyspace.getUserDefinedTypes()).thenReturn(ImmutableMap.of(CqlIdentifier.fromCql(NAME), mock(UserDefinedType.class)));
        CqlSession session = mock(CqlSession.class);
        when(session.executeReactive(any(SimpleStatement.class))).thenReturn(new ReactiveResultSet() {
            @NotNull
            @Override
            public Publisher<? extends ColumnDefinitions> getColumnDefinitions() {
                return Mono.empty();
            }

            @NotNull
            @Override
            public Publisher<? extends ExecutionInfo> getExecutionInfos() {
                return Mono.empty();
            }

            @NotNull
            @Override
            public Publisher<Boolean> wasApplied() {
                return Mono.just(true);
            }

            @Override
            public void subscribe(Subscriber<? super ReactiveRow> s) {
                s.onComplete();
            }
        });

        assertThat(TYPE.initialize(keyspace, session).block())
                .isEqualByComparingTo(ALREADY_DONE);

        verify(session, never()).executeReactive(STATEMENT.build());
    }

    @ParameterizedTest
    @MethodSource
    void initializationStatusReduceShouldFallIntoTheRightState(InitializationStatus left, InitializationStatus right, InitializationStatus expectedResult) {
        assertThat(left.reduce(right)).isEqualByComparingTo(expectedResult);
    }

    static Stream<Arguments> initializationStatusReduceShouldFallIntoTheRightState() {
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