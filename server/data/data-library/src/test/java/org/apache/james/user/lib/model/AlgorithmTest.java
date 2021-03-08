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

import static org.apache.james.user.lib.model.Algorithm.DEFAULT_FACTORY;
import static org.apache.james.user.lib.model.Algorithm.LEGACY_FACTORY;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class AlgorithmTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Algorithm.class).verify();
    }

    @Test
    void ofShouldParseRawHash() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(DEFAULT_FACTORY.of("SHA-1").asString()).isEqualTo("SHA-1");
            softly.assertThat(DEFAULT_FACTORY.of("SHA-1").isLegacy()).isFalse();
        });
    }

    @Test
    void ofShouldParseLegacy() {
        SoftAssertions.assertSoftly(softly-> {
            softly.assertThat(LEGACY_FACTORY.of("SHA-1").asString()).isEqualTo("SHA-1");
            softly.assertThat(LEGACY_FACTORY.of("SHA-1").isLegacy()).isTrue();
        });
    }
}