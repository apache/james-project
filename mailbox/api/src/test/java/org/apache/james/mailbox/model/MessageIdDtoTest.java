/*
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
package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MessageIdDtoTest {

    private static final TestMessageId.Factory factory = new TestMessageId.Factory();
    private static final Long SAMPLE_ID_VALUE = 42L;
    private static final String SAMPLE_ID_STRING = SAMPLE_ID_VALUE.toString();
    private static final TestMessageId SAMPLE_ID = TestMessageId.of(SAMPLE_ID_VALUE);

    @Test
    void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(MessageIdDto.class).verify();
    }

    @Test
    void shouldAcceptStringAndGiveItBack() {
        assertThat(new MessageIdDto(SAMPLE_ID_STRING).asString())
            .isEqualTo(SAMPLE_ID_STRING);
    }

    @Test
    void shouldAcceptMessageIdAndGiveTheRightString() {
        assertThat(new MessageIdDto(SAMPLE_ID).asString())
            .isEqualTo(SAMPLE_ID_STRING);
    }

    @Test
    void shouldAcceptMessageIdAndGiveItBack() {
        assertThat(new MessageIdDto(SAMPLE_ID).instantiate(factory))
            .isEqualTo(SAMPLE_ID);
    }

    @Test
    void shouldAcceptStringAndGiveAnInstantiatedMessageId() {
        assertThat(new MessageIdDto(SAMPLE_ID_STRING).instantiate(factory))
            .isEqualTo(SAMPLE_ID);
    }

    @Test
    void shouldThrowAnExceptionOnWronglyFormattedString() {
        MessageIdDto messageIdDto = new MessageIdDto("Definitively not a number");

        assertThatThrownBy(() -> messageIdDto.instantiate(factory))
            .isInstanceOf(Exception.class);
    }
}
