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

package org.apache.james.jmap.memory.projections;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.EmailQueryViewContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MemoryEmailQueryViewTest implements EmailQueryViewContract {
    private static final boolean COLLAPSE_THREAD = true;

    private MemoryEmailQueryView testee;

    @BeforeEach
    void setUp() {
        testee = new MemoryEmailQueryView();
    }

    @Override
    public EmailQueryView testee() {
        return testee;
    }

    @Override
    public MailboxId mailboxId1() {
        return TestId.of(0);
    }

    @Override
    public MessageId messageId1() {
        return TestMessageId.of(1);
    }

    @Override
    public MessageId messageId2() {
        return TestMessageId.of(2);
    }

    @Override
    public MessageId messageId3() {
        return TestMessageId.of(3);
    }

    @Override
    public MessageId messageId4() {
        return TestMessageId.of(4);
    }

    @Override
    public ThreadId threadId() {
        return ThreadId.fromBaseMessageId(TestMessageId.of(1));
    }

    @Test
    public void listMailboxContentSortedBySentAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    public void listMailboxContentSortedBySentAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_2, DATE_6, messageId3(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentSortedBySentAt(mailboxId1(), Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId4(), messageId3());
    }

    @Test
    public void listMailboxContentSinceReceivedAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_3, Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    public void listMailboxContentSinceReceivedAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedBySentAt(mailboxId1(), DATE_1, Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    public void listMailboxContentSinceSentdAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_2, Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    public void listMailboxContentSinceSentAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentSinceSentAt(mailboxId1(), DATE_1, Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    public void listMailboxContentSortedByReceivedAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentSortedByReceivedAt(mailboxId1(), Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    public void listMailboxContentSortedByReceivedAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentSortedByReceivedAt(mailboxId1(), Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    public void listMailboxContentSinceSortedByReceivedAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedByReceivedAt(mailboxId1(), DATE_4, Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3());
    }

    @Test
    public void listMailboxContentSinceSortedByReceivedAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentSinceAfterSortedByReceivedAt(mailboxId1(), DATE_4, Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId3(), messageId2());
    }

    @Test
    public void listMailboxContentBeforeSortedByReceivedAtShouldReturnLatestMessageOfThreadWhenCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_4, messageId1(), threadId()).block();
        testee().save(mailboxId1(), DATE_2, DATE_3, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), threadId()).block();

        assertThat(testee().listMailboxContentBeforeSortedByReceivedAt(mailboxId1(), DATE_4, Limit.limit(12), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId1());
    }

    @Test
    public void listMailboxContentBeforeSortedByReceivedAtShouldApplyLimitWithCollapseThreads() {
        testee().save(mailboxId1(), DATE_1, DATE_2, messageId1(), ThreadId.fromBaseMessageId(TestMessageId.of(2))).block();
        testee().save(mailboxId1(), DATE_3, DATE_4, messageId2(), threadId()).block();
        testee().save(mailboxId1(), DATE_5, DATE_6, messageId3(), ThreadId.fromBaseMessageId(TestMessageId.of(3))).block();
        testee().save(mailboxId1(), DATE_4, DATE_5, messageId4(), threadId()).block();

        assertThat(testee().listMailboxContentBeforeSortedByReceivedAt(mailboxId1(), DATE_5, Limit.limit(2), COLLAPSE_THREAD).collectList().block())
            .containsExactly(messageId4(), messageId1());
    }
}
