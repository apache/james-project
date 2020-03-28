/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailboxCountersTest {

    public static final TestId MAILBOX_ID = TestId.of(36);

    @Test
    void mailboxCountersShouldRespectBeanContract() {
        EqualsVerifier.forClass(MailboxCounters.class).verify();
    }

    @Test
    void sanitizeShouldCorrectNegativeCount() {
        assertThat(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(-1)
                .unseen(0)
                .build()
                .sanitize())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(0)
                .unseen(0)
                .build());
    }

    @Test
    void sanitizeShouldCorrectNegativeUnseen() {
        assertThat(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(12)
                .unseen(-1)
                .build()
                .sanitize())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(12)
                .unseen(0)
                .build());
    }

    @Test
    void sanitizeShouldCorrectUnseenExceedingCount() {
        assertThat(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(12)
                .unseen(36)
                .build()
                .sanitize())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(12)
                .unseen(12)
                .build());
    }

    @Test
    void sanitizeShouldNoopWhenValid() {
        assertThat(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(36)
                .unseen(12)
                .build()
                .sanitize())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(36)
                .unseen(12)
                .build());
    }
}
