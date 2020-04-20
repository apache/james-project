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

package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class NullableMessageSequenceNumberTest {

    @Test
    void ofShouldThrowOnNegative() {
        assertThatThrownBy(() -> NullableMessageSequenceNumber.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldNotThrowOnZero() {
        assertThatCode(() -> NullableMessageSequenceNumber.of(0)).doesNotThrowAnyException();
    }

    @Test
    void ofShouldNotThrowOnPositiveValue() {
        assertThatCode(() -> NullableMessageSequenceNumber.of(12)).doesNotThrowAnyException();
    }

    @Test
    void asIntShouldReturnIntWhenMessage() {
        assertThat(NullableMessageSequenceNumber.of(12).asInt()).contains(12);
    }

    @Test
    void asIntShouldReturnEmptyWhenNoMessage() {
        assertThat(NullableMessageSequenceNumber.noMessage().asInt()).isEmpty();
    }

    @Test
    void foldShouldCallOnlyFirstMethodOnNoMessage() {
        assertThatCode(
            () -> NullableMessageSequenceNumber.noMessage().fold(() -> 12, msn -> fail()))
            .doesNotThrowAnyException();
    }

    @Test
    void foldShouldCallOnlySecondMethodOnMessage() {
        assertThatCode(
            () -> NullableMessageSequenceNumber.of(24).fold(Assertions::fail, msn -> 12))
            .doesNotThrowAnyException();
    }

    @Test
    void ifPresentShouldNotCallMethodOnNoMessage() {
        assertThatCode(
            () -> NullableMessageSequenceNumber.noMessage().ifPresent(ignored -> Assertions.fail()))
            .doesNotThrowAnyException();
    }

    @Test
    void ifPresentShouldCallMethodOnWithMessage() {
        AtomicReference<MessageSequenceNumber> ref = new AtomicReference<>();
        NullableMessageSequenceNumber.of(24).ifPresent(ref::set);
        assertThat(ref.get()).isNotNull();
    }

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(NullableMessageSequenceNumber.class).verify();
    }

}