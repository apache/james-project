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

import org.apache.james.util.ValuePatch;
import org.junit.jupiter.api.Test;

class VacationPatchTest {

    static final ZonedDateTime DATE_2014 = ZonedDateTime.parse("2014-04-03T02:01+07:00[Asia/Vientiane]");
    static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-04-03T02:01+07:00[Asia/Vientiane]");
    static final ZonedDateTime DATE_2017 = ZonedDateTime.parse("2017-04-03T02:01+07:00[Asia/Vientiane]");
    static final Vacation VACATION = Vacation.builder()
        .fromDate(Optional.of(DATE_2014))
        .toDate(Optional.of(DATE_2015))
        .enabled(true)
        .subject(Optional.of("subject"))
        .textBody("anyMessage")
        .htmlBody("html Message")
        .build();

    @Test
    void fromDateShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().fromDate((ValuePatch<ZonedDateTime>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toDateShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().toDate((ValuePatch<ZonedDateTime>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void textBodyShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().textBody((ValuePatch<String>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void htmlBodyShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().htmlBody((ValuePatch<String>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void subjectShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().subject((ValuePatch<String>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isEnabledShouldThrowNPEOnNullInput() {
        assertThatThrownBy(() -> VacationPatch.builder().isEnabled((ValuePatch<Boolean>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isIdentityShouldBeTrueWhenUpdateIsEmpty() {
        assertThat(
            VacationPatch.builder()
                .build()
                .isIdentity())
            .isTrue();
    }

    @Test
    void isIdentityShouldBeFalseWhenUpdateIsNotEmpty() {
        assertThat(
            VacationPatch.builder()
                .subject(ValuePatch.modifyTo("any subject"))
                .build()
                .isIdentity())
            .isFalse();
    }

    @Test
    void builderShouldWellSetFields() {
        ValuePatch<String> subject = ValuePatch.modifyTo("subject");
        ValuePatch<String> htmlBody = ValuePatch.modifyTo("html text");
        ValuePatch<String> textBody = ValuePatch.modifyTo("simple text");
        ValuePatch<Boolean> isEnabled = ValuePatch.modifyTo(true);

        VacationPatch update = VacationPatch.builder()
            .fromDate(ValuePatch.modifyTo(DATE_2014))
            .toDate(ValuePatch.modifyTo(DATE_2015))
            .subject(subject)
            .htmlBody(htmlBody)
            .textBody(textBody)
            .isEnabled(isEnabled)
            .build();

        assertThat(update.getFromDate()).isEqualTo(ValuePatch.modifyTo(DATE_2014));
        assertThat(update.getToDate()).isEqualTo(ValuePatch.modifyTo(DATE_2015));
        assertThat(update.getSubject()).isEqualTo(subject);
        assertThat(update.getHtmlBody()).isEqualTo(htmlBody);
        assertThat(update.getTextBody()).isEqualTo(textBody);
        assertThat(update.getIsEnabled()).isEqualTo(isEnabled);
    }

    @Test
    void patchVacationShouldUpdateEnabled() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .isEnabled(ValuePatch.modifyTo(true))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .enabled(true)
                .build());
    }

    @Test
    void patchVacationShouldUpdateFromDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .fromDate(ValuePatch.modifyTo(DATE_2014))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .fromDate(Optional.of(DATE_2014))
                .enabled(false)
                .build());
    }

    @Test
    void patchVacationShouldUpdateToDate() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .toDate(ValuePatch.modifyTo(DATE_2017))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .toDate(Optional.of(DATE_2017))
                .enabled(false)
                .build());
    }

    @Test
    void patchVacationShouldUpdateSubject() {
        String newSubject = "new subject";
        VacationPatch vacationPatch = VacationPatch.builder()
            .subject(ValuePatch.modifyTo(newSubject))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .subject(Optional.of(newSubject))
                .enabled(false)
                .build());
    }

    @Test
    void patchVacationShouldUpdateTextBody() {
        String newTextBody = "new text body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .textBody(ValuePatch.modifyTo(newTextBody))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .textBody(newTextBody)
                .enabled(false)
                .build());
    }

    @Test
    void patchVacationShouldUpdateHtmlBody() {
        String newHtmlBody = "new <b>html</b> body";
        VacationPatch vacationPatch = VacationPatch.builder()
            .htmlBody(ValuePatch.modifyTo(newHtmlBody))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(Vacation.builder()
                .enabled(false)
                .htmlBody(newHtmlBody)
                .build());
    }

    @Test
    void patchVacationShouldAllowToUpdateAllFieldsAtOnce() {
        VacationPatch vacationPatch = VacationPatch.builder()
            .subject(ValuePatch.ofOptional(VACATION.getSubject()))
            .textBody(ValuePatch.ofOptional(VACATION.getTextBody()))
            .htmlBody(ValuePatch.ofOptional(VACATION.getHtmlBody()))
            .fromDate(ValuePatch.ofOptional(VACATION.getFromDate()))
            .toDate(ValuePatch.ofOptional(VACATION.getToDate()))
            .isEnabled(ValuePatch.modifyTo(VACATION.isEnabled()))
            .build();

        assertThat(vacationPatch.patch(VacationRepository.DEFAULT_VACATION))
            .isEqualTo(VACATION);
    }

    @Test
    void emptyPatchesShouldNotChangeExistingVacations() {
        assertThat(VacationPatch.builder()
            .build()
            .patch(VACATION))
            .isEqualTo(VACATION);
    }

    @Test
    void nullUpdateShouldResetSubject() {
        Vacation vacation = VacationPatch.builderFrom(VACATION)
            .subject(ValuePatch.remove())
            .build()
            .patch(VACATION);

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
    void nullUpdateShouldResetText() {
        Vacation vacation = VacationPatch.builderFrom(VACATION)
            .textBody(ValuePatch.remove())
            .build()
            .patch(VACATION);

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
    void nullUpdateShouldResetHtml() {
        Vacation vacation = VacationPatch.builderFrom(VACATION)
            .htmlBody(ValuePatch.remove())
            .build()
            .patch(VACATION);

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
    void nullUpdateShouldResetToDate() {
        Vacation vacation = VacationPatch.builderFrom(VACATION)
            .toDate(ValuePatch.remove())
            .build()
            .patch(VACATION);

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
    void nullUpdateShouldResetFromDate() {
        Vacation vacation = VacationPatch.builderFrom(VACATION)
            .fromDate(ValuePatch.remove())
            .build()
            .patch(VACATION);

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

}
