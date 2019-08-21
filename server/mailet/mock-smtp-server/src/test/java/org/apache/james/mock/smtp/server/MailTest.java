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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailTest {
    private MailAddress bob;
    private MailAddress alice;
    private Mail.Envelope envelope;

    @BeforeEach
    void setUp() throws Exception {
        bob = new MailAddress("bob@domain.tld");
        alice = new MailAddress("alice@domain.tld");
        envelope = new Mail.Envelope(alice, ImmutableList.of(bob));
    }

    @Nested
    class EnvelopeTest {
        @Test
        void shouldMatchBeanContract() {
           EqualsVerifier.forClass(Mail.Envelope.class)
               .verify();
        }

        @Test
        void constructorShouldThrowWhenNullFrom() {
            assertThatThrownBy(() -> new Mail.Envelope(null, ImmutableList.of(bob)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructorShouldThrowWhenNullRecipients() {
            assertThatThrownBy(() -> new Mail.Envelope(bob, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void constructorShouldThrowWhenEmptyRecipients() {
            assertThatThrownBy(() -> new Mail.Envelope(bob, ImmutableList.of()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldMatchBeanContract() {
       EqualsVerifier.forClass(Mail.class)
           .verify();
    }

    @Test
    void constructorShouldThrowWhenNullMessage() {
        assertThatThrownBy(() -> new Mail(envelope, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenNullEnvelope() {
        assertThatThrownBy(() -> new Mail(null, "header: single header message dude"))
            .isInstanceOf(NullPointerException.class);
    }
}