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

class DurationParserTest {

    @Test
    void getMilliSecondsShouldConvertValueWhenNoUnitAmountAsString() {
        Duration actual = DurationParser.parse("2");
        assertThat(actual).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void getMilliSecondsShouldUseProvidedUnitWhenNoUnitAmountAsString() {
        Duration actual = DurationParser.parse("2", ChronoUnit.SECONDS);
        assertThat(actual).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void getMilliSecondsShouldNotUseProvidedUnitWhenNoUnitAmountAsString() {
        Duration actual = DurationParser.parse("2 minutes", ChronoUnit.SECONDS);
        assertThat(actual).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMsecAmountAsString() {
        Duration actual = DurationParser.parse("2 msec");
        assertThat(actual).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMsAmountAsString() {
        Duration actual = DurationParser.parse("2 ms");
        assertThat(actual).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMsCapitalAmountAsString() {
        Duration actual = DurationParser.parse("2 Ms");
        assertThat(actual).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMsecsAmountAsString() {
        Duration actual = DurationParser.parse("2 msecs");
        assertThat(actual).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenSAmountAsString() {
        Duration actual = DurationParser.parse("2 s");
        assertThat(actual).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenSecAmountAsString() {
        Duration actual = DurationParser.parse("2 sec");
        assertThat(actual).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenSecCapitalAmountAsString() {
        Duration actual = DurationParser.parse("2 Sec");
        assertThat(actual).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenSecsAmountAsString() {
        Duration actual = DurationParser.parse("2 secs");
        assertThat(actual).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMAmountAsString() {
        Duration actual = DurationParser.parse("2 m");
        assertThat(actual).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMinuteAmountAsString() {
        Duration actual = DurationParser.parse("2 minute");
        assertThat(actual).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMinuteCapitalAmountAsString() {
        Duration actual = DurationParser.parse("2 Minute");
        assertThat(actual).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenMinutesAmountAsString() {
        Duration actual = DurationParser.parse("2 minutes");
        assertThat(actual).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenHAmountAsString() {
        Duration actual = DurationParser.parse("2 h");
        assertThat(actual).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenHourAmountAsString() {
        Duration actual = DurationParser.parse("2 hour");
        assertThat(actual).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenHourCapitalAmountAsString() {
        Duration actual = DurationParser.parse("2 Hour");
        assertThat(actual).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenHoursAmountAsString() {
        Duration actual = DurationParser.parse("2 hours");
        assertThat(actual).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenDAmountAsString() {
        Duration actual = DurationParser.parse("2 d");
        assertThat(actual).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenDayAmountAsString() {
        Duration actual = DurationParser.parse("2 day");
        assertThat(actual).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenDayCapitalAmountAsString() {
        Duration actual = DurationParser.parse("2 Day");
        assertThat(actual).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void getMilliSecondsShouldConvertValueWhenDaysAmountAsString() {
        Duration actual = DurationParser.parse("2 days");
        assertThat(actual).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void getMilliSecondsShouldThrowWhenIllegalUnitInRawString() {
        assertThatThrownBy(() -> DurationParser.parse("2 week"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void getMilliSecondsShouldThrowWhenIllegalPattern() {
        assertThatThrownBy(() -> DurationParser.parse("illegal pattern"))
            .isInstanceOf(NumberFormatException.class);
    }
}
