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

package org.apache.james.webadmin.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.james.vacation.api.Vacation;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VacationDTOTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(VacationDTO.class).verify();
    }

    @Test
    public void fromShouldThrowOnNull() {
        assertThatThrownBy(() -> VacationDTO.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromShouldSetAllFields() {
        Vacation vacation = Vacation.builder()
            .enabled(false)
            .fromDate(Optional.of(ZonedDateTime.parse("2021-09-13T10:00:00Z")))
            .toDate(Optional.of(ZonedDateTime.parse("2021-09-20T19:00:00Z")))
            .subject(Optional.of("I am on vacation"))
            .textBody(Optional.of("I am on vacation, will be back soon."))
            .htmlBody(Optional.of("<p>I am on vacation, will be back soon.</p>"))
            .build();

        VacationDTO dto = VacationDTO.from(vacation);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(dto.getEnabled()).isNotEmpty();
            softly.assertThat(dto.getEnabled().get()).isEqualTo(vacation.isEnabled());
            softly.assertThat(dto.getFromDate()).isEqualTo(vacation.getFromDate());
            softly.assertThat(dto.getToDate()).isEqualTo(vacation.getToDate());
            softly.assertThat(dto.getSubject()).isEqualTo(vacation.getSubject());
            softly.assertThat(dto.getTextBody()).isEqualTo(vacation.getTextBody());
            softly.assertThat(dto.getHtmlBody()).isEqualTo(vacation.getHtmlBody());
        });
    }
}
