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
package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurationParserTest {
    @Test
    void parseShouldUseMsAsDefaultUnit() {
        assertThat(DurationParser.parse("2"))
            .isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void parseShouldUseSpecifiedDefaultUnit() {
        assertThat(DurationParser.parse("2", ChronoUnit.SECONDS))
            .isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void parseShouldUseSpecifiedUnit() {
        assertThat(DurationParser.parse("2 minutes", ChronoUnit.SECONDS))
            .isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void parseShouldSupportStartingSpaces() {
        assertThat(DurationParser.parse("  2 minutes"))
            .isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void parseShouldSupportEndingSpaces() {
        assertThat(DurationParser.parse("2 minutes  "))
            .isEqualTo(Duration.ofMinutes(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "2 ms", "2 msec", "2 msecs", "2 Ms"})
    void parseShouldHandleMilliseconds(String input) {
        assertThat(DurationParser.parse(input))
            .isEqualTo(Duration.ofMillis(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2 s", "2 sec", "2 Sec", "2 second", "2 seconds"})
    void parseShouldHandleSeconds(String input) {
        assertThat(DurationParser.parse(input))
            .isEqualTo(Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2 m", "2 min", "2 mins", "2 minute", "2 Minute", "2 minutes"})
    void parseShouldHandleMinutes(String input) {
        assertThat(DurationParser.parse(input))
            .isEqualTo(Duration.ofMinutes(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2 h", "2 hour", "2 Hour", "2 hours"})
    void parseShouldHandleHours(String input) {
        assertThat(DurationParser.parse(input))
            .isEqualTo(Duration.ofHours(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2 d", "2 day", "2 Day", "2 days"})
    void parseShouldHandleDays(String input) {
        assertThat(DurationParser.parse(input))
            .isEqualTo(Duration.ofDays(2));
    }

    @Test
    void parseShouldThrowWhenIllegalUnitInRawString() {
        assertThatThrownBy(() -> DurationParser.parse("2 unknown"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseShouldThrowWhenMissingAmount() {
        assertThatThrownBy(() -> DurationParser.parse("seconds"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseShouldThrowWhenMissingAmountWithExtraWhiteSpace() {
        assertThatThrownBy(() -> DurationParser.parse(" seconds"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> DurationParser.parse(""))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseShouldThrowWhenNegativeAmount() {
        assertThatThrownBy(() -> DurationParser.parse("-1 s"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parseShouldThrowWhenZero() {
        assertThat(DurationParser.parse("0 s"))
            .isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    void parseShouldThrowWhenIllegalPattern() {
        assertThatThrownBy(() -> DurationParser.parse("illegal pattern"))
            .isInstanceOf(NumberFormatException.class);
    }
}
