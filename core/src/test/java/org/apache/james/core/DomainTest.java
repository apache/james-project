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

package org.apache.james.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


class DomainTest {
    @Test
    void testPlainDomain() {
        Domain d1 = Domain.of("example.com");
        assertThat(d1.name().equals(d1.asString()));
        Domain d2 = Domain.of("Example.com");
        assertThat(d2.name()).isNotEqualTo(d2.asString());
        assertThat(d1.asString()).isEqualTo(d2.asString());
    }

    @Test
    void testIPv4Domain() {
        Domain d1 = Domain.of("192.0.4.1");
        assertThat(d1.asString()).isEqualTo("192.0.4.1");
    }

    @Test
    void testPunycodeIDN() {
        Domain d1 = Domain.of("xn--gr-zia.example");
        assertThat(d1.asString()).isEqualTo("grå.example");
    }

    @Test
    void testDevanagariDomain() {
        Domain d1 = Domain.of("डाटामेल.भारत");
        assertThat(d1.asString()).isEqualTo(d1.name());
    }

    private static Stream<Arguments> malformedDomains() {
        return Stream.of(
                         "😊☺️.example", // emoji not permitted by IDNA
                         "#.example", // really and truly not permitted
                         "\uFEFF.example", // U+FEFF is the byte order mark
                         "\u200C.example", // U+200C is a zero-width non-joiner
                         "\u200Eibm.example" // U+200E is left-to-right
                         )
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("malformedDomains")
    void testMalformedDomains(String malformed) {
        assertThatThrownBy(() -> Domain.of(malformed))
            .as("rejecting malformed domain " + malformed)
            .isInstanceOf(IllegalArgumentException.class);
    }
}


