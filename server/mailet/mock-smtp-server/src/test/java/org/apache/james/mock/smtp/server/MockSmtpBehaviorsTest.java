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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;
import nl.jqno.equalsverifier.EqualsVerifier;

class MockSmtpBehaviorsTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule());

    private static final String JSON = "[" + MockSMTPBehaviorTest.JSON_ALL_FIELDS + ", "
        + MockSMTPBehaviorTest.JSON_COMPULSORY_FIELDS + "]";

    private static final MockSmtpBehaviors POJO = new MockSmtpBehaviors(ImmutableList.of(
        MockSMTPBehaviorTest.POJO_ALL_FIELDS,
        MockSMTPBehaviorTest.POJO_COMPULSORY_FIELDS));

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MockSmtpBehaviors.class)
            .verify();
    }

    @Test
    void jacksonShouldDeserializeBehaviors() throws Exception {
        MockSmtpBehaviors behaviors = OBJECT_MAPPER.readValue("[" + MockSMTPBehaviorTest.JSON_ALL_FIELDS + ", "
            + MockSMTPBehaviorTest.JSON_COMPULSORY_FIELDS + "]", MockSmtpBehaviors.class);

        assertThat(behaviors)
            .isEqualTo(new MockSmtpBehaviors(ImmutableList.of(
                MockSMTPBehaviorTest.POJO_ALL_FIELDS,
                MockSMTPBehaviorTest.POJO_COMPULSORY_FIELDS)));
    }

    @Test
    void jacksonShouldSerializeBehaviors() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(POJO);

        assertThatJson(json)
            .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER))
            .isEqualTo(JSON);
    }
}