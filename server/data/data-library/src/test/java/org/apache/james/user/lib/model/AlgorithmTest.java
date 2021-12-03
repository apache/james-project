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

package org.apache.james.user.lib.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class AlgorithmTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Algorithm.class)
            .withIgnoredFields("hasher")
            .verify();
    }

    @Test
    void ofShouldParseRawHash() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1").asString()).isEqualTo("SHA-1/plain");
            softly.assertThat(Algorithm.of("SHA-1").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseRawHashWithFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "plain").asString()).isEqualTo("SHA-1/plain");
            softly.assertThat(Algorithm.of("SHA-1", "plain").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1", "plain").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacy() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/legacy").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1/legacy").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacyWithFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1", "legacy").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "legacy").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1", "legacy").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1", "legacy").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacyIgnoringFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").asString()).isEqualTo("SHA-1/legacy");
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy", "plain").isSalted()).isFalse();
        });
    }

    @Test
    void ofShouldParseSalted() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/salted").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1/salted").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1/salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseSaltedWithFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1", "salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "salted").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1", "salted").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1", "salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseSaltedIgnoringFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").asString()).isEqualTo("SHA-1/salted");
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").isLegacy()).isFalse();
            softly.assertThat(Algorithm.of("SHA-1/salted", "plain").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySalted() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySaltedWithFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1", "legacy_salted").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseLegacySaltedIgnoringFallback() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").getName()).isEqualTo("SHA-1");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").asString()).isEqualTo("SHA-1/legacy_salted");
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").isLegacy()).isTrue();
            softly.assertThat(Algorithm.of("SHA-1/legacy_salted", "plain").isSalted()).isTrue();
        });
    }

    @Test
    void ofShouldParseIterationAndKeySize() {
        assertThat(Algorithm.of("PBKDF2-10-20", "plain").hasher())
            .isEqualTo(new Algorithm.PBKDF2Hasher(10, 20));
    }

    @Test
    void ofShouldParseIteration() {
        assertThat(Algorithm.of("PBKDF2-10", "plain").hasher())
            .isEqualTo(new Algorithm.PBKDF2Hasher(10, 1024));
    }

    @Test
    void ofShouldAcceptDefaultPBKDF2() {
        assertThat(Algorithm.of("PBKDF2", "plain").hasher())
            .isEqualTo(new Algorithm.PBKDF2Hasher(1000, 1024));
    }

    @Test
    void ofShouldThrowOnBadIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-bad", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnEmptyIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnNegativeIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2--1", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnEmptyKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnBadKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-bad", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnZeroIteration() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-0", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnZeroKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1-0", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldThrowOnNegativeKeySize() {
        assertThatThrownBy(() -> Algorithm.of("PBKDF2-1--1", "plain"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}