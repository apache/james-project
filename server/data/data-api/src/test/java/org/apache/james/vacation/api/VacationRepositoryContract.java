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

package org.apache.james.vacation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.util.ValuePatch;
import org.junit.jupiter.api.Test;

public interface VacationRepositoryContract {

    AccountId ACCOUNT_ID = AccountId.fromString("identifier");
    ZonedDateTime DATE_2014 = ZonedDateTime.parse("2014-04-03T02:01+07:00[Asia/Vientiane]");
    ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-04-03T02:01+07:00[Asia/Vientiane]");
    ZonedDateTime DATE_2016 = ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]");
    ZonedDateTime DATE_2017 = ZonedDateTime.parse("2017-04-03T02:01+07:00[Asia/Vientiane]");
    Vacation VACATION = Vacation.builder()
        .fromDate(Optional.of(DATE_2015))
        .toDate(Optional.of(DATE_2016))
        .enabled(true)
        .subject(Optional.of("subject"))
        .textBody("anyMessage")
        .htmlBody("html Message")
        .build();


    VacationRepository vacationRepository();

    @Test
    default void retrieveVacationShouldReturnDefaultValueByDefault() {
        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block()).isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    default void modifyVacationShouldUpdateEnabled() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .isEnabled(true)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .enabled(true)
                .build());
    }

    @Test
    default void modifyVacationShouldUpdateFromDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .fromDate(DATE_2014)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .fromDate(Optional.of(DATE_2014))
                .enabled(false)
                .build());
    }

    @Test
    default void modifyVacationShouldUpdateToDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .toDate(DATE_2017)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .toDate(Optional.of(DATE_2017))
                .enabled(false)
                .build());
    }

    @Test
    default void modifyVacationShouldUpdateSubject() {
        String newSubject = "new subject";
        VacationPatch vacationPatch = VacationPatch.builder()
            .subject(newSubject)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .subject(Optional.of(newSubject))
                .enabled(false)
                .build());
    }

    @Test
    default void modifyVacationShouldUpdateTextBody() {
        String newTextBody = "new text body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .textBody(newTextBody)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .textBody(newTextBody)
                .enabled(false)
                .build());
    }

    @Test
    default void modifyVacationShouldUpdateHtmlBody() {
        String newHtmlBody = "new <b>html</b> body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .htmlBody(newHtmlBody)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(Vacation.builder()
                .enabled(false)
                .htmlBody(newHtmlBody)
                .build());
    }

    @Test
    default void modifyVacationShouldAllowToUpdateAllFieldsAtOnce() {
        VacationPatch vacationPatch = VacationPatch.builderFrom(VACATION)
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VACATION);
    }

    @Test
    default void modifyVacationShouldAllowEmptyUpdates() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .build();

        vacationRepository().modifyVacation(ACCOUNT_ID, vacationPatch).block();

        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VacationRepository.DEFAULT_VACATION);
    }

    @Test
    default void emptyUpdatesShouldNotChangeExistingVacations() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .build())
            .block();

        // Then
        assertThat(vacationRepository().retrieveVacation(ACCOUNT_ID).block())
            .isEqualTo(VACATION);
    }

    @Test
    default void nullUpdateShouldResetSubject() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .subject(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
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
    default void nullUpdateShouldResetText() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .textBody(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
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
    default void nullUpdateShouldResetHtml() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .htmlBody(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
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
    default void nullUpdateShouldResetToDate() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .toDate(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
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
    default void nullUpdateShouldResetFromDate() {
        // Given
        vacationRepository().modifyVacation(ACCOUNT_ID,
            VacationPatch.builderFrom(VACATION)
                .build())
            .block();

        // When
        vacationRepository().modifyVacation(ACCOUNT_ID, VacationPatch.builder()
            .fromDate(ValuePatch.remove())
            .build())
            .block();

        // Then
        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
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

    @Test
    default void retrieveVacationShouldThrowOnNullAccountId() {
        assertThatThrownBy(() -> vacationRepository().retrieveVacation(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void modifyVacationShouldThrowOnNullAccountId() {
        assertThatThrownBy(() -> vacationRepository().modifyVacation(null, VacationPatch.builder().build()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void modifyVacationShouldThrowOnNullVacation() {
        assertThatThrownBy(() -> vacationRepository().modifyVacation(ACCOUNT_ID, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void retrieveVacationShouldIgnoreCase() {
        vacationRepository().modifyVacation(ACCOUNT_ID,
                VacationPatch.builderFrom(VACATION)
                    .build())
            .block();

        AccountId upperCaseAccount = AccountId.fromString(ACCOUNT_ID.getIdentifier().toUpperCase());
        Vacation vacation = vacationRepository().retrieveVacation(upperCaseAccount).block();
        assertThat(vacation).isNotNull();
    }

    @Test
    default void modifiyVacationShouldIgnoreCase() {
        AccountId upperCaseAccount = AccountId.fromString(ACCOUNT_ID.getIdentifier().toUpperCase());
        vacationRepository().modifyVacation(upperCaseAccount,
                VacationPatch.builderFrom(VACATION)
                    .build())
            .block();

        Vacation vacation = vacationRepository().retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation).isNotNull();
    }
}
