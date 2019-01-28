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

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.util.ValuePatch;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractVacationRepositoryTest {

    public static final AccountId ACCOUNT_ID = AccountId.fromString("identifier");
    public static final ZonedDateTime DATE_2014 = ZonedDateTime.parse("2014-04-03T02:01+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-04-03T02:01+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_2016 = ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_2017 = ZonedDateTime.parse("2017-04-03T02:01+07:00[Asia/Vientiane]");
    public static final Vacation VACATION = Vacation.builder()
        .fromDate(Optional.of(DATE_2015))
        .toDate(Optional.of(DATE_2016))
        .enabled(true)
        .subject(Optional.of("subject"))
        .textBody("anyMessage")
        .htmlBody("html Message")
        .build();


    private VacationRepository vacationRepository;

    protected abstract VacationRepository createVacationRepository();

    @Before
    public void setUp() throws Exception {
        vacationRepository = createVacationRepository();
    }

    @Test
    public void retrieveVacationShouldReturnDefaultValueByDefault() {
        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block()).isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    public void modifyVacationShouldUpdateEnabled() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .isEnabled(true)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .enabled(true)
                .build());
    }

    @Test
    public void modifyVacationShouldUpdateFromDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .fromDate(DATE_2014)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .fromDate(Optional.of(DATE_2014))
                .enabled(false)
                .build());
    }

    @Test
    public void modifyVacationShouldUpdateToDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .toDate(DATE_2017)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .toDate(Optional.of(DATE_2017))
                .enabled(false)
                .build());
    }

    @Test
    public void modifyVacationShouldUpdateSubject() {
        String newSubject = "new subject";
        VacationPatch vacationPatch = VacationPatch.builder()
            .subject(newSubject)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .subject(Optional.of(newSubject))
                .enabled(false)
                .build());
    }

    @Test
    public void modifyVacationShouldUpdateTextBody() {
        String newTextBody = "new text body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .textBody(newTextBody)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .textBody(newTextBody)
                .enabled(false)
                .build());
    }

    @Test
    public void modifyVacationShouldUpdateHtmlBody() {
        String newHtmlBody = "new <b>html</b> body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .htmlBody(newHtmlBody)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .enabled(false)
                .htmlBody(newHtmlBody)
                .build());
    }

    @Test
    public void modifyVacationShouldAllowToUpdateAllFieldsAtOnce() {
        VacationPatch vacationPatch = VacationPatch.builderFrom(VACATION)
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VACATION);
    }

    @Test
    public void modifyVacationShouldAllowEmptyUpdates() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .build();

        vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    public void emptyUpdatesShouldNotChangeExistingVacations() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .build())
            .block();

        // Then
        assertThat(vacationRepository.retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VACATION);
    }

    @Test
    public void nullUpdateShouldResetSubject() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .subject(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation.getSubject()).isEmpty();
        assertThat(vacation)
            .isEqualTo(Vacation.builder()
                .fromDate(VACATION.getFromDate())
                .toDate(VACATION.getToDate())
                .enabled(VACATION.isEnabled())
                .textBody(VACATION.getTextBody())
                .htmlBody(VACATION.getHtmlBody())
                .build());
    }

    @Test
    public void nullUpdateShouldResetText() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .textBody(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation.getTextBody()).isEmpty();
        assertThat(vacation)
            .isEqualTo(Vacation.builder()
                .fromDate(VACATION.getFromDate())
                .toDate(VACATION.getToDate())
                .enabled(VACATION.isEnabled())
                .subject(VACATION.getSubject())
                .htmlBody(VACATION.getHtmlBody())
                .build());
    }

    @Test
    public void nullUpdateShouldResetHtml() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .htmlBody(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation.getHtmlBody()).isEmpty();
        assertThat(vacation)
            .isEqualTo(Vacation.builder()
                .fromDate(VACATION.getFromDate())
                .toDate(VACATION.getToDate())
                .enabled(VACATION.isEnabled())
                .subject(VACATION.getSubject())
                .textBody(VACATION.getTextBody())
                .build());
    }

    @Test
    public void nullUpdateShouldResetToDate() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .toDate(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation.getToDate()).isEmpty();
        assertThat(vacation)
            .isEqualTo(Vacation.builder()
                .fromDate(VACATION.getFromDate())
                .enabled(VACATION.isEnabled())
                .subject(VACATION.getSubject())
                .textBody(VACATION.getTextBody())
                .htmlBody(VACATION.getHtmlBody())
                .build());
    }

    @Test
    public void nullUpdateShouldResetFromDate() {
        // Given
        vacationRepository.modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository.modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .fromDate(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation.getFromDate()).isEmpty();
        assertThat(vacation)
            .isEqualTo(Vacation.builder()
                .toDate(VACATION.getToDate())
                .enabled(VACATION.isEnabled())
                .subject(VACATION.getSubject())
                .textBody(VACATION.getTextBody())
                .htmlBody(VACATION.getHtmlBody())
                .build());
    }

    @Test(expected = NullPointerException.class)
    public void retrieveVacationShouldThrowOnNullAccountId() {
        vacationRepository.retrieveVacation(null);
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullAccountId() {
        vacationRepository.modifyVacation(null, VacationPatch.builder().build());
    }

    @Test(expected = NullPointerException.class)
    public void modifyVacationShouldThrowOnNullVacation() {
        vacationRepository.modifyVacation(ACCOUNT_ID, null);
    }

}
