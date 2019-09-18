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
package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class SetVacationRequestTest {

    public static final String VACATION_ID = "singleton";

    @Test
    public void setVacationRequestShouldBeConstructedWithTheRightInformation() {
        VacationResponse vacationResponse = VacationResponse.builder().id(VACATION_ID).textBody(Optional.of("any message")).build();
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of(VACATION_ID, vacationResponse))
            .build();

        assertThat(setVacationRequest.getUpdate()).containsExactly(new AbstractMap.SimpleEntry<>(VACATION_ID, vacationResponse));
    }

    @Test(expected = NotImplementedException.class)
    public void accountIdIsNotImplemented() {
        VacationResponse vacationResponse = VacationResponse.builder().id(VACATION_ID).textBody(Optional.of("any message")).build();
        SetVacationRequest.builder()
            .accountId("any")
            .update(ImmutableMap.of(VACATION_ID, vacationResponse))
            .build();
    }

    @Test
    public void accountIdNullShouldBeConsideredAsNoAccountId() {
        VacationResponse vacationResponse = VacationResponse.builder().id(VACATION_ID).textBody(Optional.of("any message")).build();
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .accountId(null)
            .update(ImmutableMap.of(VACATION_ID, vacationResponse))
            .build();

        assertThat(setVacationRequest.getUpdate()).containsEntry(VACATION_ID, vacationResponse);
    }

}
