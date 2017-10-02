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

package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.util.EventCollector;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MailboxEventDispatcherTest {
    private static final int sessionId = 10;
    private static final int MOD_SEQ = -1;
    public static final Condition<MailboxListener.Event> INSTANCE_OF_EVENT_FLAGS_UPDATED = new Condition<MailboxListener.Event>() {
        @Override
        public boolean matches(MailboxListener.Event event) {
            return event instanceof MailboxListener.FlagsUpdated;
        }
    };
    public static final TestId MAILBOX_ID = TestId.of(147L);
    public static final int UID_VALIDITY = 145;

    private MailboxEventDispatcher dispatcher;
    private EventCollector collector;
    private MessageResult result;
    private Mailbox mailbox;

    private MailboxSession session = new MockMailboxSession("test") {
        @Override
        public long getSessionId() {
            return sessionId;
        }
    };

    @Before
    public void setUp() throws Exception {
        collector = new EventCollector();

        dispatcher = MailboxEventDispatcher.ofListener(collector);
        result = mock(MessageResult.class);
        mailbox = new SimpleMailbox(MailboxPath.forUser("user", "name"), UID_VALIDITY, MAILBOX_ID);

        when(result.getUid()).thenReturn(MessageUid.of(23));
    }


    @Test
    public void testShouldReturnNoChangesWhenSystemFlagsUnchanged() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags(Flags.Flag.DELETED))
                .oldFlags(new Flags(Flags.Flag.DELETED))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator()).isEmpty();
    }

    @Test
    public void testShouldShowAnsweredAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(
                UpdatedFlags.builder()
                    .uid(result.getUid())
                    .modSeq(MOD_SEQ)
                    .newFlags(new Flags(Flags.Flag.ANSWERED))
                    .oldFlags(new Flags())
                    .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.ANSWERED);
    }

    @Test
    public void testShouldShowAnsweredRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(
                UpdatedFlags.builder()
                    .uid(result.getUid())
                    .modSeq(MOD_SEQ)
                    .newFlags(new Flags(Flags.Flag.ANSWERED))
                    .oldFlags(new Flags())
                    .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.ANSWERED);
    }

    @Test
    public void testShouldShowDeletedAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.DELETED))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.DELETED);
    }

    @Test
    public void testShouldShowDeletedRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags(Flags.Flag.DELETED))
                .oldFlags(new Flags())
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.DELETED);
    }

    @Test
    public void testShouldShowDraftAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.DRAFT))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.DRAFT);
    }

    @Test
    public void testShouldShowDraftRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.DRAFT))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.DRAFT);
    }

    @Test
    public void testShouldShowFlaggedAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags(Flags.Flag.FLAGGED))
                .oldFlags(new Flags())
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.FLAGGED);
    }

    @Test
    public void testShouldShowFlaggedRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.FLAGGED))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.FLAGGED);
    }

    @Test
    public void testShouldShowRecentAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags(Flags.Flag.RECENT))
                .oldFlags(new Flags())
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.RECENT);
    }

    @Test
    public void testShouldShowRecentRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.RECENT))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.RECENT);
    }

    @Test
    public void testShouldShowSeenAdded() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags(Flags.Flag.SEEN))
                .oldFlags(new Flags())
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.SEEN);
    }

    @Test
    public void testShouldShowSeenRemoved() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(new Flags())
                .oldFlags(new Flags(Flags.Flag.SEEN))
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
            .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.SEEN);
    }

    @Test
    public void testShouldShowMixedChanges() {
        dispatcher.flagsUpdated(session,
            ImmutableList.of(result.getUid()),
            mailbox,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(result.getUid())
                .modSeq(MOD_SEQ)
                .newFlags(FlagsBuilder.builder()
                    .add(Flags.Flag.ANSWERED, Flags.Flag.DRAFT, Flags.Flag.SEEN)
                    .build())
                .oldFlags(FlagsBuilder.builder()
                    .add(Flags.Flag.DRAFT, Flags.Flag.RECENT)
                    .build())
                .build()));

        assertThat(collector.getEvents()).hasSize(1)
            .are(INSTANCE_OF_EVENT_FLAGS_UPDATED);
        MailboxListener.FlagsUpdated event = (MailboxListener.FlagsUpdated) collector.getEvents()
                .get(0);
        assertThat(event.getUpdatedFlags().get(0).systemFlagIterator())
            .containsOnly(Flags.Flag.SEEN, Flags.Flag.RECENT, Flags.Flag.ANSWERED);
    }

    @Test
    public void expungedShouldNotFireEventWhenEmptyMap() {
        dispatcher.expunged(session, ImmutableMap.<MessageUid, MessageMetaData> of(), mailbox);
        assertThat(collector.getEvents()).isEmpty();
    }

    @Test
    public void flagsUpdatedShouldNotFireEventWhenEmptyIdList() {
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
                .uid(MessageUid.of(1))
                .modSeq(2)
                .oldFlags(new Flags(Flag.RECENT))
                .newFlags(new Flags(Flag.ANSWERED))
                .build();
        
        dispatcher.flagsUpdated(session, ImmutableList.<MessageUid> of(), mailbox, ImmutableList.of(updatedFlags));
        assertThat(collector.getEvents()).isEmpty();
    }
}
