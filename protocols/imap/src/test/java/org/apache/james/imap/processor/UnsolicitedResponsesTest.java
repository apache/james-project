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

package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.response.ExistsResponse;
import org.apache.james.imap.message.response.ExpungeResponse;
import org.apache.james.imap.processor.base.SelectedMailboxImpl;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.util.MDCBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UnsolicitedResponsesTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxSession.SessionId OTHER_SESSION_ID = MailboxSession.SessionId.of(2);
    private static final MailboxPath PATH = MailboxPath.inbox(BOB);
    private static final TestId MAILBOX_ID = TestId.of(42);
    private static final MessageUid UID_2 = MessageUid.of(2);

    private final AbstractMailboxProcessor<ImapRequest> processor = new AbstractMailboxProcessor<>(ImapRequest.class, null, null, null) {
        @Override
        protected MDCBuilder mdc(ImapRequest message) {
            return MDCBuilder.create();
        }
    };

    private MessageManager messageManager;
    private FakeImapSession imapSession;
    private SelectedMailboxImpl selected;

    @BeforeEach
    void setUp() {
        messageManager = mock(MessageManager.class);
        when(messageManager.getId()).thenReturn(MAILBOX_ID);
        MailboxSession mailboxSession = MailboxSessionUtil.create(BOB);
        imapSession = new FakeImapSession();
        imapSession.setMailboxSession(mailboxSession);
        imapSession.authenticated();
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getMailboxReactive(any(MailboxId.class), any(MailboxSession.class))).thenReturn(Mono.just(messageManager));
        selected = new SelectedMailboxImpl(mailboxManager, null, mailboxSession, messageManager);
        imapSession.selected(selected).block();
        deliver(added(1, 2, 3));
    }

    @Test
    void expungeReceivedWhileWritingResponsesShouldBeReportedNextTime() {
        deliver(expunged(1));

        unsolicitedResponses(response -> {
            if (response instanceof ExpungeResponse) {
                deliver(expunged(2));
            }
        });

        assertThat(selected.expungedUids()).containsExactly(UID_2);
        assertThat(unsolicitedResponses(response -> { }))
            .filteredOn(ExpungeResponse.class::isInstance)
            .hasSize(1);
        assertThat(selected.msn(UID_2).asInt()).isEmpty();
    }

    @Test
    void additionReceivedWhileWritingResponsesShouldBeReportedNextTime() {
        unsolicitedResponses(response -> {
            if (response instanceof ExistsResponse) {
                deliver(added(4));
            }
        });

        assertThat(unsolicitedResponses(response -> { }))
            .filteredOn(ExistsResponse.class::isInstance)
            .extracting(response -> ((ExistsResponse) response).getNumberOfMessages())
            .containsExactly(4L);
    }

    @Test
    void flagUpdateReceivedWhileWritingResponsesShouldBeReportedNextTime() {
        unsolicitedResponses(response -> { });
        deliver(flagsUpdated(1));
        when(messageManager.listMessagesMetadata(any(), any())).thenAnswer(invocation -> {
            // The flags of UID 1 are being fetched when UID 2 gets flagged
            deliver(flagsUpdated(2));
            return Flux.empty();
        });

        unsolicitedResponses(response -> { });

        assertThat(selected.flagUpdateUids()).containsExactly(UID_2);
    }

    private List<ImapResponseMessage> unsolicitedResponses(Consumer<ImapResponseMessage> onResponse) {
        List<ImapResponseMessage> responses = new ArrayList<>();
        ImapProcessor.Responder responder = new ImapProcessor.Responder() {
            @Override
            public void respond(ImapResponseMessage message) {
                responses.add(message);
                onResponse.accept(message);
            }

            @Override
            public void flush() {
            }
        };
        processor.unsolicitedResponses(imapSession, responder, false, false).block();
        return responses;
    }

    private void deliver(Event event) {
        Mono.from(selected.reactiveEvent(event)).block();
    }

    private static TreeMap<MessageUid, MessageMetaData> metaData(long... uids) {
        TreeMap<MessageUid, MessageMetaData> result = new TreeMap<>();
        for (long uid : uids) {
            MessageUid messageUid = MessageUid.of(uid);
            result.put(messageUid, new MessageMetaData(messageUid, ModSeq.of(uid), new Flags(), 10, new Date(), Optional.empty(), null, null));
        }
        return result;
    }

    private static Added added(long... uids) {
        return new Added(OTHER_SESSION_ID, BOB, PATH, MAILBOX_ID, metaData(uids), Event.EventId.random(),
            !Added.IS_DELIVERY, Added.IS_APPENDED, Optional.empty());
    }

    private static Expunged expunged(long... uids) {
        return new Expunged(OTHER_SESSION_ID, BOB, PATH, MAILBOX_ID, metaData(uids), Event.EventId.random(), Optional.empty());
    }

    private static FlagsUpdated flagsUpdated(long uid) {
        return new FlagsUpdated(OTHER_SESSION_ID, BOB, PATH, MAILBOX_ID,
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(uid))
                .modSeq(ModSeq.of(uid))
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.SEEN))
                .build()),
            Event.EventId.random());
    }
}
