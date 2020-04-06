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

package org.apache.james.backends.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.core.Version;

import nl.jqno.equalsverifier.EqualsVerifier;

class RabbitMQServerVersionTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RabbitMQServerVersion.class)
            .verify();
    }

    @ParameterizedTest
    @MethodSource("versionsToParse")
    void shouldParseVersion(String input, Version expected) {
        assertThat(RabbitMQServerVersion.of(input)).isEqualTo(new RabbitMQServerVersion(expected));
    }

    @ParameterizedTest
    @MethodSource("versionsComparison")
    void shouldBeAtLeast(String lower, String upper) {
        RabbitMQServerVersion lowerVersion = RabbitMQServerVersion.of(lower);
        RabbitMQServerVersion upperVersion = RabbitMQServerVersion.of(upper);
        assertThat(upperVersion.isAtLeast(lowerVersion)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("versionsReversedComparison")
    void shouldNotBeAtLeastWhenReversed(String lower, String upper) {
        RabbitMQServerVersion lowerVersion = RabbitMQServerVersion.of(lower);
        RabbitMQServerVersion upperVersion = RabbitMQServerVersion.of(upper);
        assertThat(lowerVersion.isAtLeast(upperVersion)).isFalse();
    }

    static Stream<Arguments> versionsToParse() {
        return Stream.of(
            Arguments.of("3.8.1", version(3, 8, 1)),
            Arguments.of("3.18.1", version(3, 18, 1)),
            Arguments.of("3.8.", version(3, 8, 0)),
            Arguments.of("3.8.0+beta.4.38.g33a7f97", version(3, 8, 0)),
            Arguments.of("3.7.1-alpha.40", version(3, 7, 1)),
            Arguments.of("3.7.0~alpha.449-1", version(3, 7, 0))
        );
    }

    static Stream<Arguments> versionsComparison() {
        return Stream.of(
            Arguments.of("3.8.1", "3.8.1"),
            Arguments.of("3.18.1", "3.18.1"),
            Arguments.of("3.8.", "3.8.0"),
            Arguments.of("3.7.5", "3.8.1"),
            Arguments.of("3.8", "3.8.1"),
            Arguments.of("3.8.0", "4.0.0")
        );
    }

    static Stream<Arguments> versionsReversedComparison() {
        return Stream.of(
            Arguments.of("3.7.5", "3.8.1"),
            Arguments.of("3.8", "3.8.1"),
            Arguments.of("3.8.0", "4.0.0")
        );
    }

    private static Version version(int major, int minor, int patch) {
        return new Version(major, minor, patch, "", "rabbitmq", "version");
    }
}