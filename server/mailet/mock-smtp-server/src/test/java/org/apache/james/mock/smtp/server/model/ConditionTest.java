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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mock.smtp.server.Fixture.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import nl.jqno.equalsverifier.EqualsVerifier;

class ConditionTest {
    @Test
    void operatorConditionShouldMatchBeanContract() {
        EqualsVerifier.forClass(Condition.OperatorCondition.class)
            .verify();
    }

    @Test
    void matchAllShouldMatchBeanContract() {
        EqualsVerifier.forClass(Condition.MatchAllCondition.class)
            .verify();
    }

    @Test
    void differentConditionTypesShouldNotBeEqual() {
        assertThat(Condition.MATCH_ALL)
            .isNotEqualTo(new Condition.OperatorCondition(Operator.CONTAINS, "any"));
    }

    @Test
    void constructorShouldThrowWhenNullOperator() {
        assertThatThrownBy(() -> new Condition.OperatorCondition(null, "matchingValue"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNullMatchingValue() {
        assertThatThrownBy(() -> new Condition.OperatorCondition(Operator.CONTAINS, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matchesShouldReturnTrueWhenOperatorMatches() {
        Condition condition = new Condition.OperatorCondition(Operator.CONTAINS, "match me");

        assertThat(condition.matches("this contains match me string"))
            .isTrue();
    }

    @Test
    void matchesShouldReturnFalseWhenOperatorDoesNotMatch() {
        Condition condition = new Condition.OperatorCondition(Operator.CONTAINS, "match me");

        assertThat(condition.matches("this contains another string"))
            .isFalse();
    }

    @Test
    void matchesShouldThrowWhenNullLine() {
        Condition condition = new Condition.OperatorCondition(Operator.CONTAINS, "match me");

        assertThatThrownBy(() -> condition.matches(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matchAllShouldReturnTrue() {
        assertThat(Condition.MATCH_ALL.matches("this contains another string"))
            .isTrue();
    }

    @Test
    void matchAllShouldReturnTrueEvenWhenLineIsNull() {
        assertThat(Condition.MATCH_ALL.matches(null))
            .isTrue();
    }

    @Test
    void matchAllShouldReturnTrueEvenWhenLineIsEmpty() {
        assertThat(Condition.MATCH_ALL.matches(""))
            .isTrue();
    }

    @Nested
    class JSONTest {
        @Test
        void jacksonShouldDeserializeCondition() throws Exception {
            Condition condition = OBJECT_MAPPER.readValue(
                "{\"operator\":\"contains\", \"matchingValue\":\"matchme\"}",
                Condition.class);

            assertThat(condition).isEqualTo(new Condition.OperatorCondition(Operator.CONTAINS, "matchme"));
        }

        @Test
        void jacksonShouldDeserializeMatchAllCondition() throws Exception {
            Condition condition = OBJECT_MAPPER.readValue(
                "{\"operator\":\"matchAll\"}",
                Condition.class);

            assertThat(condition).isEqualTo(Condition.MATCH_ALL);
        }

        @Test
        void jacksonShouldSerializeCondition() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(new Condition.OperatorCondition(Operator.CONTAINS, "matchme"));

            assertThatJson(json).isEqualTo("{\"operator\":\"contains\", \"matchingValue\":\"matchme\"}");
        }

        @Test
        void jacksonShouldSerializeMatchAllCondition() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(Condition.MATCH_ALL);

            assertThatJson(json).isEqualTo("{\"operator\":\"matchAll\"}");
        }

        @Test
        void jacksonShouldThrowWhenDeserializeMatchAllConditionWithMatchingValue() {
            String json = "{\"operator\":\"matchAll\", \"matchingValue\":\"matchme\"}";

            assertThatThrownBy(() -> OBJECT_MAPPER.readValue(json, Condition.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasMessageContaining("You should not specify a matchingValue with the matchAll operator");
        }

        @Test
        void jacksonShouldThrowWhenDeserializeContainsConditionWithoutMatchingValue() {
            String json = "{\"operator\":\"contains\"}";

            assertThatThrownBy(() -> OBJECT_MAPPER.readValue(json, Condition.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasMessageContaining("You need to specify a matchingValue with the contains operator");
        }
    }
}
