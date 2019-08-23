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

package org.apache.james.mock.smtp.server;

import static org.apache.james.mock.smtp.server.Fixture.BEHAVIORS;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_COMPULSORY_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SMTPBehaviorRepositoryTest {
    private SMTPBehaviorRepository testee;

    @BeforeEach
    void setUp() {
        testee = new SMTPBehaviorRepository();
    }

    @Test
    void getBehaviorsShouldReturnEmptyWhenNoValueStored() {
        assertThat(testee.getBehaviors())
            .isEmpty();
    }

    @Test
    void getBehaviorsShouldReturnPreviouslyStoredValue() {
        testee.setBehaviors(BEHAVIORS);

        assertThat(testee.getBehaviors()).contains(BEHAVIORS);
    }

    @Test
    void getBehaviorsShouldReturnLatestStoredValue() {
        MockSmtpBehaviors newPojo = new MockSmtpBehaviors(ImmutableList.of(BEHAVIOR_COMPULSORY_FIELDS));

        testee.setBehaviors(BEHAVIORS);
        testee.setBehaviors(newPojo);

        assertThat(testee.getBehaviors()).contains(newPojo);
    }

    @Test
    void getBehaviorsShouldReturnEmptyWhenCleared() {
        testee.setBehaviors(BEHAVIORS);

        testee.clearBehaviors();

        assertThat(testee.getBehaviors()).isEmpty();
    }
}