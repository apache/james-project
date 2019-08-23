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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_ALL_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.BEHAVIOR_COMPULSORY_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.JSON_BEHAVIOR_ALL_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.JSON_BEHAVIOR_COMPULSORY_FIELDS;
import static org.apache.james.mock.smtp.server.Fixture.OBJECT_MAPPER;
import static org.apache.james.mock.smtp.server.Fixture.RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;
import nl.jqno.equalsverifier.EqualsVerifier;

class MockSMTPBehaviorTest {
    @Nested
    class NumberOfAnswersPolicyTest {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(MockSMTPBehavior.NumberOfAnswersPolicy.class)
                .verify();
        }

        @Test
        void timesShouldThrowWhenNegativeValue() {
            assertThatThrownBy(() -> MockSMTPBehavior.NumberOfAnswersPolicy.times(-1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void timesShouldThrowWhenZero() {
            assertThatThrownBy(() -> MockSMTPBehavior.NumberOfAnswersPolicy.times(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getNumberOfAnswersShouldReturnEmptyWhenAlways() {
            assertThat(MockSMTPBehavior.NumberOfAnswersPolicy.anytime().getNumberOfAnswers())
                .isEmpty();
        }

        @Test
        void getNumberOfAnswersShouldReturnSpecifiedNumberWhenTimes() {
            assertThat(MockSMTPBehavior.NumberOfAnswersPolicy.times(5).getNumberOfAnswers())
                .contains(5);
        }
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MockSMTPBehavior.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenCommandIsNull() {
        assertThatThrownBy(() -> new MockSMTPBehavior(null, Optional.empty(), RESPONSE, MockSMTPBehavior.NumberOfAnswersPolicy.anytime()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenConditionIsNull() {
        assertThatThrownBy(() -> new MockSMTPBehavior(SMTPCommand.NOOP, null, RESPONSE, MockSMTPBehavior.NumberOfAnswersPolicy.anytime()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenResponseIsNull() {
        assertThatThrownBy(() -> new MockSMTPBehavior(SMTPCommand.NOOP, Optional.empty(), null, MockSMTPBehavior.NumberOfAnswersPolicy.anytime()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNumberOfAnswersIsNull() {
        assertThatThrownBy(() -> new MockSMTPBehavior(SMTPCommand.NOOP, Optional.empty(), RESPONSE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Nested
    class JSONTest {
        @Test
        void jacksonShouldDeserializeMockSMTPBehaviorWithAllField() throws Exception {
            MockSMTPBehavior behavior = OBJECT_MAPPER.readValue(JSON_BEHAVIOR_ALL_FIELDS, MockSMTPBehavior.class);

            assertThat(behavior)
                .isEqualTo(BEHAVIOR_ALL_FIELDS);
        }

        @Test
        void jacksonShouldSerializeMockSMTPBehaviorWithAllField() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(BEHAVIOR_ALL_FIELDS);

            assertThatJson(json)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT))
                .isEqualTo(JSON_BEHAVIOR_ALL_FIELDS);
        }

        @Test
        void jacksonShouldDeserializeMockSMTPBehaviorWithCompulsoryField() throws Exception {
            MockSMTPBehavior behavior = OBJECT_MAPPER.readValue(JSON_BEHAVIOR_COMPULSORY_FIELDS, MockSMTPBehavior.class);

            assertThat(behavior)
                .isEqualTo(BEHAVIOR_COMPULSORY_FIELDS);
        }

        @Test
        void jacksonShouldSerializeMockSMTPBehaviorWithCompulsoryField() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(BEHAVIOR_COMPULSORY_FIELDS);

            assertThatJson(json)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT))
                .isEqualTo(JSON_BEHAVIOR_COMPULSORY_FIELDS);
        }
    }
}
