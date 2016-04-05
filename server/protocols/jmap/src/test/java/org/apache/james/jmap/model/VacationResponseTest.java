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

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

public class VacationResponseTest {

    public static final String IDENTIFIER = "identifier";
    public static final String MESSAGE = "A message explaining I am in vacation";
    public static final ZonedDateTime FROM_DATE = ZonedDateTime.parse("2016-04-15T11:56:32.224+07:00[Asia/Vientiane]");
    public static final ZonedDateTime TO_DATE = ZonedDateTime.parse("2016-04-16T11:56:32.224+07:00[Asia/Vientiane]");

    @Test
    public void vacationResponseBuilderShouldBeConstructedWithTheRightInformation() {
        VacationResponse vacationResponse = VacationResponse.builder()
            .id(IDENTIFIER)
            .enabled(true)
            .fromDate(Optional.of(FROM_DATE))
            .toDate(Optional.of(TO_DATE))
            .textBody(MESSAGE)
            .build();

        assertThat(vacationResponse.getId()).isEqualTo(IDENTIFIER);
        assertThat(vacationResponse.isEnabled()).isEqualTo(true);
        assertThat(vacationResponse.getTextBody()).isEqualTo(MESSAGE);
        assertThat(vacationResponse.getFromDate()).contains(FROM_DATE);
        assertThat(vacationResponse.getToDate()).contains(TO_DATE);
    }

    @Test(expected = IllegalStateException.class)
    public void vacationResponseBuilderRequiresABodyText() {
        VacationResponse.builder()
            .id(IDENTIFIER)
            .enabled(true)
            .build();
    }

}
