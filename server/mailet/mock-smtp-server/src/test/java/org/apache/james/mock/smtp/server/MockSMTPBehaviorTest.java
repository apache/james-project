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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.mock.smtp.server.Response.SMTPStatusCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;
import nl.jqno.equalsverifier.EqualsVerifier;

class MockSMTPBehaviorTest {
    private static final Response RESPONSE = Response.serverAccept(SMTPStatusCode.ACTION_COMPLETE_250, "message");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    static final String JSON_COMPULSORY_FIELDS = "{" +
        "  \"response\": {\"code\":250, \"message\":\"OK\", \"rejected\":false}," +
        "  \"command\": \"EHLO\"" +
        "}";

    static final MockSMTPBehavior POJO_COMPULSORY_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        Optional.empty(),
        Response.serverAccept(Response.SMTPStatusCode.of(250), "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.anytime());

    static final String JSON_ALL_FIELDS = "{" +
        "  \"response\": {\"code\":250, \"message\":\"OK\", \"rejected\":false}," +
        "  \"condition\": {\"operator\":\"contains\", \"matchingValue\":\"matchme\"}," +
        "  \"command\": \"EHLO\"," +
        "  \"numberOfAnswer\": 7" +
        "}";

    static final MockSMTPBehavior POJO_ALL_FIELDS = new MockSMTPBehavior(
        SMTPCommand.EHLO,
        Optional.of(new Condition.OperatorCondition(Operator.CONTAINS, "matchme")),
        Response.serverAccept(Response.SMTPStatusCode.of(250), "OK"),
        MockSMTPBehavior.NumberOfAnswersPolicy.times(7));

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
            MockSMTPBehavior behavior = OBJECT_MAPPER.readValue(JSON_ALL_FIELDS, MockSMTPBehavior.class);

            assertThat(behavior)
                .isEqualTo(POJO_ALL_FIELDS);
        }

        @Test
        void jacksonShouldSerializeMockSMTPBehaviorWithAllField() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(POJO_ALL_FIELDS);

            assertThatJson(json)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT))
                .isEqualTo(JSON_ALL_FIELDS);
        }

        @Test
        void jacksonShouldDeserializeMockSMTPBehaviorWithCompulsoryField() throws Exception {
            MockSMTPBehavior behavior = OBJECT_MAPPER.readValue(JSON_COMPULSORY_FIELDS, MockSMTPBehavior.class);

            assertThat(behavior)
                .isEqualTo(POJO_COMPULSORY_FIELDS);
        }

        @Test
        void jacksonShouldSerializeMockSMTPBehaviorWithCompulsoryField() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(POJO_COMPULSORY_FIELDS);

            assertThatJson(json)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT))
                .isEqualTo(JSON_COMPULSORY_FIELDS);
        }
    }
}
