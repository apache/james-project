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

import java.time.DayOfWeek;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ImapDateTimeFormatterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void dayOfWeekShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    public void parseShouldNotThrowWhenDayOfWeekIsAbsent() {
        ZonedDateTime.parse("28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenDayOfWeekIsWrong() {
        expectedException.expect(DateTimeParseException.class);
        // must be wednesday
        ZonedDateTime.parse("Mon, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenDayOfWeekIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Abc, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void dayOfWeekShouldBeParsedWhenOneDigit() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfMonth()).isEqualTo(3);
    }

    @Test
    public void dayOfWeekShouldBeParsedWhenTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("13 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getDayOfMonth()).isEqualTo(13);
    }

    @Test
    public void parseShouldThrowWhenDayOfMonthIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenDayOfMonthIsNegative() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("-2 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenDayOfMonthIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("64 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void monthOfYearShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMonth()).isEqualTo(Month.JUNE);
    }

    @Test
    public void parseShouldThrowWhenMonthOfYearIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Wed, 28 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenMonthOfYearIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Wed, 28 Abc 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void yearShouldBeParsedWhenFourDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("Wed, 28 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(2017);
    }

    @Test
    public void yearShouldBeParsedWhenTwoDigitsGreaterThanInitialYear() {
        ZonedDateTime dateTime = ZonedDateTime.parse("28 Jun 77 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(1977);
    }

    @Test
    public void yearShouldBeParsedWhenTwoDigitsLesserThanInitialYear() {
        ZonedDateTime dateTime = ZonedDateTime.parse("28 Jun 64 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getYear()).isEqualTo(2064);
    }

    @Test
    public void parseShouldThrowWhenYearIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Wed, 28 Jun 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenYearIsLesserThanTwoDigits() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Wed, 28 Jun 1 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenYearIsGreaterThanFourDigits() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("Wed, 28 Jun 12345 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void hourOfDayShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getHour()).isEqualTo(4);
    }

    @Test
    public void parseShouldNotThrowWhenHourOfDayIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 4:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getHour()).isEqualTo(4);
    }

    @Test
    public void parseShouldThrowWhenHourOfDayIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 :35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenHourOfDayIsGreaterThanTwoDigits() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 123:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenHourOfDayIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 48:35:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void minuteOfHourShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMinute()).isEqualTo(35);
    }

    @Test
    public void parseShouldNotThrowWhenMinuteOfHourIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:5:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getMinute()).isEqualTo(5);
    }

    @Test
    public void parseShouldThrowWhenMinuteOfHourIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04::11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenMinuteOfHourIsGreaterThanTwoDigits() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:123:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenMinuteOfHourDayIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:72:11 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void secondOfMinuteShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getSecond()).isEqualTo(11);
    }

    @Test
    public void parseShouldNotThrowWhenSecondOfMinuteIsLesserThanTwoDigits() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:1 -0700", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getSecond()).isEqualTo(1);
    }

    @Test
    public void parseShouldNotThrowWhenSecondOfMinuteIsAbsent() {
        ZonedDateTime.parse("28 Jun 2017 04:35 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenSecondOfMinuteIsGreaterThanTwoDigits() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:35:123 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenSecondOfMinuteDayIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:35:78 -0700", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void offsetShouldBeParsed() {
        ZonedDateTime dateTime = ZonedDateTime.parse("3 Jun 2017 04:35:11 -0712", ImapDateTimeFormatter.rfc5322());
        assertThat(dateTime.getOffset()).isEqualTo(ZoneOffset.ofHoursMinutes(-7, -12));
    }

    @Test
    public void parseShouldThrowWhenOffsetIsAbsent() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:35:11", ImapDateTimeFormatter.rfc5322());
    }

    @Test
    public void parseShouldThrowWhenOffsetIsUnknow() {
        expectedException.expect(DateTimeParseException.class);
        ZonedDateTime.parse("3 Jun 2017 04:35:11 +7894", ImapDateTimeFormatter.rfc5322());
    }
}
