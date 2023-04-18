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

package org.apache.james.jmap.api.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.Test;

public interface EmailQueryViewContract {
    ZonedDateTime DATE_1 = ZonedDateTime.parse("2010-10-30T15:12:00Z");
    ZonedDateTime DATE_2 = ZonedDateTime.parse("2010-10-30T16:12:00Z");
    ZonedDateTime DATE_3 = ZonedDateTime.parse("2010-10-30T17:12:00Z");
    ZonedDateTime DATE_4 = ZonedDateTime.parse("2010-10-30T18:12:00Z");
    ZonedDateTime DATE_5 = ZonedDateTime.parse("2010-10-30T19:12:00Z");
    ZonedDateTime DATE_6 = ZonedDateTime.parse("2010-10-30T20:12:00Z");
    ZonedDateTime DATE_7 = ZonedDateTime.parse("2010-10-30T21:12:00Z");

    EmailQueryView testee();

    MailboxId mailboxId1();

    MessageId messageId1();

    MessageId messageId2();

    MessageId messageId3();

    MessageId messageId4();

    @Test
    default void listMailboxContentShouldReturnEmptyByDefault() {
        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listMailboxContentShouldBeOrderedBySentAt() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .containsExactly(messageId2(), messageId3(), messageId1());
    }

    @Test
    default void listMailboxContentShouldApplyLimit() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(2)).collectList().block())
            .containsExactly(messageId2(), messageId3());
    }

    @Test
    default void listMailboxContentSinceReceivedAtShouldExcludeTooOldItems() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_3, Limit.limit(12)).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    default void listMailboxContentSinceReceivedAtShouldReturnEmptyWhenNoneMatch() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_7, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listMailboxContentSinceReceivedAtAtShouldApplyLimit() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_1, Limit.limit(2)).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    default void listMailboxContentSinceSentdAtShouldExcludeTooOldItems() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_2, Limit.limit(12)).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    default void listMailboxContentSinceSentAtAtShouldApplyLimit() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_1, Limit.limit(2)).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    default void listMailboxContentSinceSentAtShouldReturnEmptyWhenNoneMatch() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_7, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listMailboxContentShouldNotReturnClearedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3()).block();

        testee().delete(mailboxId1()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listMailboxContentShouldNotReturnDeletedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3()).block();

        testee().delete(mailboxId1(), messageId2()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .containsExactly(messageId3(), messageId1());
    }

    @Test
    default void listMailboxContentSinceReceivedAtShouldNotReturnClearedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        testee().delete(mailboxId1()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_4, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void listMailboxContentSinceSentAtShouldNotReturnClearedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        testee().delete(mailboxId1()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_4, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    default void saveShouldBeIdempotent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .containsExactly(messageId1());
    }

    @Test
    default void datesCanBeDuplicated() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId2()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .containsExactly(messageId1(), messageId2());
    }

    @Test
    default void listMailboxContentSinceReceivedAtShouldNotReturnDeletedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        testee().delete(mailboxId1(), messageId2()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_3, Limit.limit(12)).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    default void listMailboxContentSinceSentAtShouldNotReturnDeletedContent() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        testee().delete(mailboxId1(), messageId2()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_3, Limit.limit(12)).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    default void listMailboxContentSortedByReceivedAtShouldBeSortedByReceivedAt() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSortedByReceivedAt(mailboxId1(), Limit.limit(12)).collectList().block())
            .containsExactly(messageId3(), messageId1(), messageId2());
    }

    @Test
    default void listMailboxContentSinceSortedByReceivedAtShouldBeSortedByReceivedAt() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedByReceivedAt(mailboxId1(), DATE_4, Limit.limit(12)).collectList().block())
            .containsExactly(messageId3(), messageId1());
    }

    @Test
    default void listMailboxContentBeforeSortedByReceivedAtShouldBeSortedByReceivedAt() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3()).block();

        assertThat(testee().listMailboxContentBeforeSortedByReceivedAt(mailboxId1(), DATE_4, Limit.limit(12)).collectList().block())
            .containsExactly(messageId1(), messageId2());
    }

    @Test
    default void clearShouldNotFailWhenEmpty() {
        assertThatCode(() -> testee().delete(mailboxId1()).block()).doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotFailWhenEmpty() {
        assertThatCode(() -> testee().delete(mailboxId1(), messageId4()).block()).doesNotThrowAnyException();
    }

    @Test
    default void listMailboxContentShouldThrowOnUndefinedLimit() {
        assertThatThrownBy(() -> testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.unlimited()).blockLast())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void listMailboxContentSinceSentAtShouldThrowOnUndefinedLimit() {
        assertThatThrownBy(() -> testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_3, Limit.unlimited()).blockLast())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void listMailboxContentSinceReceivedAtShouldThrowOnUndefinedLimit() {
        assertThatThrownBy(() -> testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_3, Limit.unlimited()).blockLast())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
