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
package org.apache.james.util.date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

class ImapDateTimeFormatterTest {

    @Test
    void dayOfWeekShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    void parseShouldNotThrowWhenDayOfWeekIsAbsent() {
        ZonedDateTime.parse("28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    void parseShouldThrowWhenDayOfWeekIsWrong() {
        // must be wednesday
        assertThatThrownBy(() -> 
        ZonedDateTime.parse("Mon, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenDayOfWeekIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Abc, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void dayOfWeekShouldBeParsedWhenOneDigit() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfMonth()).isEqualTo(3);
    }

    @Test
    void dayOfWeekShouldBeParsedWhenTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("13 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfMonth()).isEqualTo(13);
    }

    @Test
    void parseShouldThrowWhenDayOfMonthIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenDayOfMonthIsNegative() {
        assertThatThrownBy(() -> ZonedDateTime.parse("-2 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenDayOfMonthIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("64 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void monthOfYearShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMonth()).isEqualTo(Month.JUNE);
    }

    @Test
    void parseShouldThrowWhenMonthOfYearIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Wed, 28 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenMonthOfYearIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Wed, 28 Abc 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void yearShouldBeParsedWhenFourDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(2017);
    }

    @Test
    void yearShouldBeParsedWhenTwoDigitsGreaterThanInitialYear() {
        ZonedDateTime dateTime = ZonedDateTime.parse("28 Jun 77 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(1977);
    }

    @Test
    void yearShouldBeParsedWhenTwoDigitsLesserThanInitialYear() {
        ZonedDateTime dateTime = ZonedDateTime.parse("28 Jun 64 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(2064);
    }

    @Test
    void parseShouldThrowWhenYearIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Wed, 28 Jun 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenYearIsLesserThanTwoDigits() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Wed, 28 Jun 1 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenYearIsGreaterThanFourDigits() {
        assertThatThrownBy(() -> ZonedDateTime.parse("Wed, 28 Jun 12345 04:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void hourOfDayShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getHour()).isEqualTo(4);
    }

    @Test
    void parseShouldNotThrowWhenHourOfDayIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 4:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getHour()).isEqualTo(4);
    }

    @Test
    void parseShouldThrowWhenHourOfDayIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 :35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenHourOfDayIsGreaterThanTwoDigits() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 123:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenHourOfDayIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 48:35:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void minuteOfHourShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMinute()).isEqualTo(35);
    }

    @Test
    void parseShouldNotThrowWhenMinuteOfHourIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:5:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMinute()).isEqualTo(5);
    }

    @Test
    void parseShouldThrowWhenMinuteOfHourIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04::11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenMinuteOfHourIsGreaterThanTwoDigits() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:123:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenMinuteOfHourDayIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:72:11 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void secondOfMinuteShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getSecond()).isEqualTo(11);
    }

    @Test
    void parseShouldNotThrowWhenSecondOfMinuteIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:1 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getSecond()).isEqualTo(1);
    }

    @Test
    void parseShouldNotThrowWhenSecondOfMinuteIsAbsent() {
        ZonedDateTime.parse("28 Jun 2017 04:35 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    void parseShouldThrowWhenSecondOfMinuteIsGreaterThanTwoDigits() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:35:123 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenSecondOfMinuteDayIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:35:78 -0700", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void offsetShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0712", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getOffset()).isEqualTo(ZoneOffset.ofHoursMinutes(-7, -12));
    }

    @Test
    void parseShouldThrowWhenOffsetIsAbsent() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:35:11", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void parseShouldThrowWhenOffsetIsUnknow() {
        assertThatThrownBy(() -> ZonedDateTime.parse("3 Jun 2017 04:35:11 +7894", ImapDateTimeFormatter.rfc5322()))
            .isInstanceOf(DateTimeParseException.class);
    }
}
