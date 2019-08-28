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

import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_ALL_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_COMPULSORY_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_MATCHING_2_TIMES;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_MATCHING_3_TIMES;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_MATCHING_EVERYTIME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SMTPBehaviorRepositoryTest {
    private SMTPBehaviorRepository testee;

    @BeforeEach
    void setUp() {
        testee = new SMTPBehaviorRepository();
    }

    @Test
    void remainingBehaviorsShouldReturnEmptyWhenNoValueStored() {
        assertThat(testee.remainingBehaviors())
            .isEmpty();
    }

    @Test
    void remainingBehaviorsShouldReturnPreviouslyStoredValue() {
        testee.setBehaviors(BEHAVIOR_ALL_FIELDS, BEHAVIOR_COMPULSORY_FIELDS);

        assertThat(testee.remainingBehaviors())
            .containsExactly(
                MockSMTPBehaviorInformation.from(BEHAVIOR_ALL_FIELDS),
                MockSMTPBehaviorInformation.from(BEHAVIOR_COMPULSORY_FIELDS));
    }

    @Test
    void remainingBehaviorsShouldReturnLatestStoredValue() {
        testee.setBehaviors(BEHAVIOR_ALL_FIELDS, BEHAVIOR_COMPULSORY_FIELDS);
        testee.setBehaviors(BEHAVIOR_COMPULSORY_FIELDS);

        assertThat(testee.remainingBehaviors())
            .containsExactly(
                MockSMTPBehaviorInformation.from(BEHAVIOR_COMPULSORY_FIELDS));
    }

    @Test
    void remainingBehaviorsShouldReturnEmptyWhenCleared() {
        testee.setBehaviors(BEHAVIOR_ALL_FIELDS, BEHAVIOR_COMPULSORY_FIELDS);

        testee.clearBehaviors();

        assertThat(testee.remainingBehaviors()).isEmpty();
    }

    @Test
    void getBehaviorInformationShouldReturnEmptyOptionalOfAnswerCountWhenUnlimitedAnswers() {
        testee.setBehaviors(BEHAVIOR_MATCHING_EVERYTIME);

        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_EVERYTIME);
        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_EVERYTIME);
        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_EVERYTIME);
        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_EVERYTIME);
        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_EVERYTIME);

        assertThat(testee.getBehaviorInformation(BEHAVIOR_MATCHING_EVERYTIME)
                .remainingAnswersCounter())
            .isEmpty();
    }

    @Test
    void decreaseRemainingAnswersShouldDecreaseLimitedAnswer() {
        testee.setBehaviors(BEHAVIOR_MATCHING_2_TIMES);

        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_2_TIMES);

        assertThat(testee.getBehaviorInformation(BEHAVIOR_MATCHING_2_TIMES)
                .remainingAnswersCounter())
            .contains(1);
    }

    @Test
    void decreaseRemainingAnswersShouldNotDecreaseOtherBehavior() {
        testee.setBehaviors(BEHAVIOR_MATCHING_2_TIMES, BEHAVIOR_MATCHING_3_TIMES);

        testee.decreaseRemainingAnswers(BEHAVIOR_MATCHING_2_TIMES);

        assertThat(testee.getBehaviorInformation(BEHAVIOR_MATCHING_3_TIMES)
                .remainingAnswersCounter())
            .contains(3);
    }
}