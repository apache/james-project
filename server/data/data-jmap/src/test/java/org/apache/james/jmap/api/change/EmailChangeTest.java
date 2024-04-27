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

package org.apache.james.jmap.api.change;

import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class EmailChangeTest {
    AccountId accountId = AccountId.fromUsername(BOB);
    ZonedDateTime date = ZonedDateTime.now();

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(EmailChange.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullAccountId() {
        assertThatThrownBy(() ->
            EmailChange.builder()
                .accountId(null)
                .state(State.of(UUID.randomUUID()))
                .date(date.minusHours(2))
                .isShared(false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullState() {
        assertThatThrownBy(() ->
            EmailChange.builder()
                .accountId(accountId)
                .state(null)
                .date(date.minusHours(2))
                .isShared(false))
            .isInstanceOf(NullPointerException.class);;
    }

    @Test
    void shouldThrowOnNullDate() {
        assertThatThrownBy(() ->
            EmailChange.builder()
                .accountId(accountId)
                .state(State.of(UUID.randomUUID()))
                .date(null)
                .isShared(false))
            .isInstanceOf(NullPointerException.class);;
    }
}