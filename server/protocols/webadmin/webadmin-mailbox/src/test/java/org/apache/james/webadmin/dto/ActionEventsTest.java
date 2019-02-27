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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActionEventsTest {
    private static final String ACTION = "reDeliver";

    @Test
    void parseShouldSucceedWithCorrectActionEventsArgument() {
        assertThat(ActionEvents.parse(ACTION)).isEqualTo(ActionEvents.reDeliver);
    }

    @Test
    void parseShouldFailWithIncorrectActionEventsArgument() {
        assertThatThrownBy(() -> ActionEvents.parse("incorrect-action"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'incorrect-action' is not a valid action query parameter");
    }

    @Test
    void parseShouldFailWithMissingActionEventsArgument() {
        assertThatThrownBy(() -> ActionEvents.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'action' url parameter is mandatory");
    }
}
