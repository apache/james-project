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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.jmap.api.vacation.Vacation;
import org.junit.Test;

public class VacationResponseTest {

    public static final String IDENTIFIER = "identifier";
    public static final String MESSAGE = "A message explaining I am in vacation";
    public static final String HTML_MESSAGE = "<p>A message explaining I am in vacation</p>";
    public static final ZonedDateTime FROM_DATE = ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]");
    public static final ZonedDateTime TO_DATE = ZonedDateTime.parse("2016-04-16T11:56:32.224+07:00[Asia/Vientiane]");
    public static final String SUBJECT = "subject";

    @Test
    public void vacationResponseBuilderShouldBeConstructedWithTheRightInformation() {
        VacationResponse vacationResponse = VacationResponse.builder()
            .id(IDENTIFIER)
            .enabled(true)
            .fromDate(Optional.of(FROM_DATE))
            .toDate(Optional.of(TO_DATE))
            .textBody(Optional.of(MESSAGE))
            .subject(Optional.of(SUBJECT))
            .htmlBody(Optional.of(HTML_MESSAGE))
            .build();

        assertThat(vacationResponse.getId()).isEqualTo(IDENTIFIER);
        assertThat(vacationResponse.isEnabled()).isEqualTo(true);
        assertThat(vacationResponse.getTextBody()).contains(MESSAGE);
        assertThat(vacationResponse.getHtmlBody()).contains(HTML_MESSAGE);
        assertThat(vacationResponse.getFromDate()).contains(FROM_DATE);
        assertThat(vacationResponse.getToDate()).contains(TO_DATE);
        assertThat(vacationResponse.getSubject()).contains(SUBJECT);
    }

    @Test
    public void vacationResponseShouldBeValidIfIdIsMissing() {
        VacationResponse vacationResponse = VacationResponse.builder().build();

        assertThat(vacationResponse.isValid()).isTrue();
    }

    @Test
    public void vacationResponseShouldBeValidIfRightId() {
        VacationResponse vacationResponse = VacationResponse.builder().id(Vacation.ID).build();

        assertThat(vacationResponse.isValid()).isTrue();
    }

    @Test
    public void vacationResponseShouldBeInvalidIfWrongId() {
        VacationResponse vacationResponse = VacationResponse.builder().id(IDENTIFIER).build();

        assertThat(vacationResponse.isValid()).isFalse();
    }

    @Test
    public void vacationResponseShouldBeValidIfEnabledSetToFalse() {
        VacationResponse vacationResponse = VacationResponse.builder().enabled(false).build();

        assertThat(vacationResponse.isValid()).isTrue();
    }

    @Test
    public void vacationResponseShouldBeValidIfEnabledSetToTrue() {
        VacationResponse vacationResponse = VacationResponse.builder().enabled(true).build();

        assertThat(vacationResponse.isValid()).isTrue();
    }

    @Test
    public void subjectShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().subject(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromDateShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().fromDate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void toDateShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().toDate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void textBodyShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().textBody(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void htmlBodyShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().htmlBody(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void idStringShouldThrowNPEOnNullValue() throws Exception {
        assertThatThrownBy(() -> VacationResponse.builder().id(null)).isInstanceOf(NullPointerException.class);
    }

}
