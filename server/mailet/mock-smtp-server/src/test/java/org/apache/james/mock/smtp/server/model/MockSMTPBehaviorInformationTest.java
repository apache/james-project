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

package org.apache.james.mock.smtp.server.model;

import static org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation.RemainingAnswersCounter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation.AnswersCounter;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation.UnlimitedAnswersCounter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class MockSMTPBehaviorInformationTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MockSMTPBehaviorInformation.class)
            .suppress(Warning.NULL_FIELDS)
            .withIgnoredFields("remainingAnswersCounter")
            .withPrefabValues(RemainingAnswersCounter.class, new UnlimitedAnswersCounter(), new UnlimitedAnswersCounter())
            .verify();
    }

    @Nested
    class UnlimitedAnswersTest {

        @Test
        void isRemainingShouldReturnTrue() {
            RemainingAnswersCounter unlimited = new UnlimitedAnswersCounter();

            assertThat(unlimited.hasRemainingAnswers())
                .isTrue();
        }

        @Test
        void isRemainingShouldReturnTrueAfterDecreasing() {
            RemainingAnswersCounter unlimited = new UnlimitedAnswersCounter();
            unlimited.decrease();
            unlimited.decrease();
            unlimited.decrease();

            assertThat(unlimited.hasRemainingAnswers())
                .isTrue();
        }

        @Test
        void remainedShouldReturnEmptyOptional() {
            RemainingAnswersCounter unlimited = new UnlimitedAnswersCounter();

            assertThat(unlimited.getValue())
                .isEmpty();
        }

        @Test
        void remainedShouldReturnEmptyOptionalAfterDecreasing() {
            RemainingAnswersCounter unlimited = new UnlimitedAnswersCounter();
            unlimited.decrease();
            unlimited.decrease();

            assertThat(unlimited.getValue())
                .isEmpty();
        }
    }

    @Nested
    class LimitedAnswersTest {

        @Test
        void constructorShouldThrowWhenPassingNegativeAnswersCount() {
            assertThatThrownBy(() -> new AnswersCounter(-100))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenPassingZeroAnswersCount() {
            assertThatThrownBy(() -> new AnswersCounter(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isRemainingShouldReturnTrueWhenNoDecrease() {
            RemainingAnswersCounter limited = AnswersCounter.remains(1);

            assertThat(limited.hasRemainingAnswers())
                .isTrue();
        }

        @Test
        void isRemainingShouldReturnTrueWhenNotEnoughDecremental() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);
            limited.decrease();
            limited.decrease();

            assertThat(limited.hasRemainingAnswers())
                .isTrue();
        }

        @Test
        void isRemainingShouldReturnFalseWhenEnoughDecremental() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);
            limited.decrease();
            limited.decrease();
            limited.decrease();

            assertThat(limited.hasRemainingAnswers())
                .isFalse();
        }

        @Test
        void isRemainingShouldThrowWhenExceededDecremental() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);
            limited.decrease();
            limited.decrease();
            limited.decrease();

            assertThatThrownBy(limited::decrease)
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void remainedShouldReturnWhenNoDecrease() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);

            assertThat(limited.getValue())
                .contains(3);
        }

        @Test
        void remainedShouldReturnWhenDecrease() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);
            limited.decrease();

            assertThat(limited.getValue())
                .contains(2);
        }

        @Test
        void remainedShouldReturnZeroWhenEnoughDecremental() {
            RemainingAnswersCounter limited = AnswersCounter.remains(3);
            limited.decrease();
            limited.decrease();
            limited.decrease();

            assertThat(limited.getValue())
                .contains(0);
        }
    }
}