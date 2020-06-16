/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.backends.cassandra.init.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.datastax.driver.core.ConsistencyLevel;
import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraConsistenciesConfigurationTest {
    @Test
    void cassandraConsistenciesConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraConsistenciesConfiguration.class)
            .verify();
    }

    @Test
    void fromStringShouldThrowOnInvalidValue() {
        assertThatThrownBy(() -> CassandraConsistenciesConfiguration.fromString("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource
    void fromStringShouldInstantiateTheRightValue(String rawValue, ConsistencyLevel expected) {
        assertThat(CassandraConsistenciesConfiguration.fromString(rawValue))
            .isEqualTo(expected);
    }

    private static Stream<Arguments> fromStringShouldInstantiateTheRightValue() {
        return Stream.of(
            Arguments.of("QUORUM", ConsistencyLevel.QUORUM),
            Arguments.of("LOCAL_QUORUM", ConsistencyLevel.LOCAL_QUORUM),
            Arguments.of("EACH_QUORUM", ConsistencyLevel.EACH_QUORUM),
            Arguments.of("SERIAL", ConsistencyLevel.SERIAL),
            Arguments.of("LOCAL_SERIAL", ConsistencyLevel.LOCAL_SERIAL)
        );
    }

    @Test
    void fromConfigurationShouldTakeTheValues() {
        String consistencyLevelRegular = "LOCAL_QUORUM";
        String consistencyLevelLightweightTransaction = "LOCAL_SERIAL";

        CassandraConfiguration configuration = CassandraConfiguration.builder()
            .consistencyLevelRegular(consistencyLevelRegular)
            .consistencyLevelLightweightTransaction(consistencyLevelLightweightTransaction)
            .build();

        CassandraConsistenciesConfiguration consistenciesConfiguration = CassandraConsistenciesConfiguration
            .fromConfiguration(configuration);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(consistenciesConfiguration.getRegular()).isEqualTo(ConsistencyLevel.LOCAL_QUORUM);
            softly.assertThat(consistenciesConfiguration.getLightweightTransaction()).isEqualTo(ConsistencyLevel.LOCAL_SERIAL);
        });
    }
}