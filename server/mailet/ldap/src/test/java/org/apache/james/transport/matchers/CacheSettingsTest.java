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

package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CacheSettingsTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(CacheSettings.class)
            .verify();
    }

    @Test
    void shouldReturnEmptyWhenEmpty() {
        assertThat(CacheSettings.parse(""))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoQuestionMark() {
        assertThat(CacheSettings.parse("abcdef"))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenCacheDisabled() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=false"))
            .isEmpty();
    }

    @Test
    void shouldBeTolerantRegardingCaseInKeys() {
        assertThat(CacheSettings.parse("abcdef?cacheEnaBLed=false"))
            .isEmpty();
    }

    @Test
    void shouldReturnDefaultWhenNoSettings() {
        assertThat(CacheSettings.parse("abcdef?"))
            .contains(new CacheSettings(Duration.ofDays(1), 10_000));
    }

    @Test
    void shouldReturnDefaultWhenEnabled() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true"))
            .contains(new CacheSettings(Duration.ofDays(1), 10_000));
    }

    @Test
    void shouldTrimKeys() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true   "))
            .contains(new CacheSettings(Duration.ofDays(1), 10_000));
    }

    @Test
    void shouldIgnoreEmptyKeys() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true&"))
            .contains(new CacheSettings(Duration.ofDays(1), 10_000));
    }

    @Test
    void shouldIgnoreUnknownSettings() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true&unrelated=ignored"))
            .contains(new CacheSettings(Duration.ofDays(1), 10_000));
    }

    @Test
    void shouldAcceptCustomSize() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true&cacheSize=1234"))
            .contains(new CacheSettings(Duration.ofDays(1), 1234));
    }

    @Test
    void shouldAcceptCustomDuration() {
        assertThat(CacheSettings.parse("abcdef?cacheEnabled=true&cacheDuration=1234minutes"))
            .contains(new CacheSettings(Duration.ofMinutes(1234), 10_000));
    }

    @Test
    void shouldRejectBadlyFormattedKey() {
        assertThatThrownBy(() -> CacheSettings.parse("abcdef?noQuestionMark"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThatThrownBy(() -> CacheSettings.parse("abcdef?cacheSize=-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}