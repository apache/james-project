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

import java.util.Optional;

import org.junit.Test;

public class GetVacationResponseTest {

    public static final String IDENTIFIER = "identifier";

    @Test
    public void getVacationResponseShouldBeConstructedWithTheRightInformation() {
        VacationResponse vacationResponse = VacationResponse.builder()
            .textBody(Optional.of("Any text"))
            .id("singleton")
            .build();
        GetVacationResponse getVacationResponse = GetVacationResponse.builder()
            .accountId(IDENTIFIER)
            .vacationResponse(vacationResponse)
            .build();

        assertThat(getVacationResponse.getAccountId()).isEqualTo(IDENTIFIER);
        assertThat(getVacationResponse.getList()).containsExactly(vacationResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getVacationResponseShouldThrowIfNoVacationResponse() {
        GetVacationResponse.builder()
            .accountId(IDENTIFIER)
            .build();
    }

}
