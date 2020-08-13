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

package org.apache.james.jmap.api.vacation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class VacationTest {
    static final ZonedDateTime DATE_TIME_2016 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]");
    static final ZonedDateTime DATE_TIME_2017 = ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]");
    static final ZonedDateTime DATE_TIME_2017_1MS = ZonedDateTime.parse("2017-10-09T08:07:06.001+07:00[Asia/Vientiane]");
    static final ZonedDateTime DATE_TIME_2018 = ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]");
    static final String TEXT_BODY = "text is required when enabled";
    static final String HTML_BODY = "<b>HTML body</b>";

    @Test
    void disabledVacationsAreNotActive() {
        assertThat(
            Vacation.builder()
                .enabled(false)
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isFalse();
    }

    @Test
    void enabledVacationWithoutDatesIsActive() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isTrue();
    }

    @Test
    void rangeShouldBeInclusiveOnFromDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2016))
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isTrue();
    }

    @Test
    void rangeShouldBeInclusiveOnToDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .toDate(Optional.of(DATE_TIME_2016))
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isTrue();
    }

    @Test
    void vacationShouldBeActiveDuringRange() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2016))
                .toDate(Optional.of(DATE_TIME_2018))
                .build()
                .isActiveAtDate(DATE_TIME_2017))
            .isTrue();
    }

    @Test
    void vacationShouldNotBeActiveAfterRange() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2016))
                .toDate(Optional.of(DATE_TIME_2017))
                .build()
                .isActiveAtDate(DATE_TIME_2018))
            .isFalse();
    }

    @Test
    void vacationShouldNotBeActiveBeforeRange() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2017))
                .toDate(Optional.of(DATE_TIME_2018))
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isFalse();
    }

    @Test
    void isActiveAtDateShouldThrowOnNullValue() {
        assertThatThrownBy(() -> Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2016))
                .toDate(Optional.of(DATE_TIME_2016))
                .build()
                .isActiveAtDate(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void vacationShouldBeActiveAfterFromDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2016))
                .build()
                .isActiveAtDate(DATE_TIME_2017))
            .isTrue();
    }

    @Test
    void vacationShouldNotBeActiveBeforeFromDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .fromDate(Optional.of(DATE_TIME_2017))
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isFalse();
    }

    @Test
    void vacationShouldNotBeActiveAfterToDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .toDate(Optional.of(DATE_TIME_2017))
                .build()
                .isActiveAtDate(DATE_TIME_2018))
            .isFalse();
    }

    @Test
    void vacationShouldBeActiveBeforeToDate() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .toDate(Optional.of(DATE_TIME_2017))
                .build()
                .isActiveAtDate(DATE_TIME_2016))
            .isTrue();
    }

    @Test
    void isActiveAtDateShouldHaveMillisecondPrecision() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .toDate(Optional.of(DATE_TIME_2017))
                .build()
                .isActiveAtDate(DATE_TIME_2017_1MS))
            .isFalse();
    }

    @Test
    void textBodyShouldBeEnoughToBuildAnActivatedVacation() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .textBody(TEXT_BODY)
                .build()
                .getTextBody())
            .contains(TEXT_BODY);
    }

    @Test
    void htmlBodyShouldBeEnoughToBuildAnActivatedVacation() {
        assertThat(
            Vacation.builder()
                .enabled(true)
                .htmlBody(HTML_BODY)
                .build()
                .getHtmlBody())
            .contains(HTML_BODY);
    }

    @Test
    void textOrHtmlBodyShouldNotBeRequiredOnUnactivatedVacation() {
        assertThat(
            Vacation.builder()
                .enabled(false)
                .build()
                .isEnabled())
            .isFalse();
    }

}
