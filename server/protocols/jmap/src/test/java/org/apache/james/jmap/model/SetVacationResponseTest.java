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

import java.util.AbstractMap;

import org.junit.Test;

public class SetVacationResponseTest {

    public static final String UPDATED_VACATION_ID = "updatedVacationId";
    public static final String NOT_UPDATED_VACATION_ID = "notUpdatedVacationId";
    public static final String ERROR_TYPE = "Test Error";
    public static final String ERROR_DESCRIPTION = "Because an error is needed";

    @Test
    public void setVacationResponseShouldBeConstructedWithTheRightInformation() {
        SetError setError = SetError.builder().type(SetError.Type.ERROR).description(ERROR_DESCRIPTION).build();
        SetVacationResponse setVacationResponse = SetVacationResponse.builder()
            .updatedId(UPDATED_VACATION_ID)
            .notUpdated(NOT_UPDATED_VACATION_ID, setError)
            .build();

        assertThat(setVacationResponse.getUpdated().get()).containsExactly(UPDATED_VACATION_ID);
        assertThat(setVacationResponse.getNotUpdated().get()).containsExactly(new AbstractMap.SimpleEntry<>(NOT_UPDATED_VACATION_ID, setError));
    }

}
