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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.processor.base.SelectedMailboxImpl;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class IdleProcessorTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxSession.SessionId OTHER_SESSION_ID = MailboxSession.SessionId.of(2);
    private static final MailboxPath PATH = MailboxPath.inbox(BOB);
    private static final TestId MAILBOX_ID = TestId.of(42);
    private static final Tag TAG = new Tag("a1");
    private static final byte[] DONE = "DONE\r\n".getBytes(StandardCharsets.US_ASCII);

    private static class LineHandlerSession extends FakeImapSession {
        private ImapLineHandler lineHandler;

        @Override
        public void pushLineHandler(ImapLineHandler lineHandler) {
            this.lineHandler = lineHandler;
        }
    }

    private final IdleProcessor processor = new IdleProcessor(null, new UnpooledStatusResponseFactory(), new RecordingMetricFactory());
    private final List<ImapResponseMessage> responses = new ArrayList<>();
    private final ImapProcessor.Responder responder = new ImapProcessor.Responder() {
        @Override
        public void respond(ImapResponseMessage message) {
            responses.add(message);
        }

        @Override
        public void flush() {
        }
    };

    private LineHandlerSession imapSession;
    private SelectedMailboxImpl selected;
    private EventListener.ReactiveEventListener idleListener;

    @BeforeEach
    void setUp() {
        MessageManager messageManager = mock(MessageManager.class);
        when(messageManager.getId()).thenReturn(MAILBOX_ID);
        MailboxSession mailboxSession = MailboxSessionUtil.create(BOB);
        imapSession = new LineHandlerSession();
        imapSession.setMailboxSession(mailboxSession);
        imapSession.authenticated();
        selected = new SelectedMailboxImpl(null, null, mailboxSession, messageManager) {
            @Override
            public void registerIdle(ReactiveEventListener idle) {
                // Kept aside: the test delivers the events to the IDLE listener itself
                idleListener = idle;
            }
        };
        imapSession.selected(selected).block();
        deliver(added(1, 2, 3, 4, 5, 6));
    }

    @Test
    void expungeDeliveredAfterDoneShouldBeLeftToTheNextCommand() {
        processor.processRequestReactive(new IdleRequest(TAG), imapSession, responder).block();
        deliver(expunged(3));

        // DONE is handled while the event bus is still delivering the expunge to the IDLE listener
        Mono.from(imapSession.lineHandler.onLine(imapSession, DONE)).block();
        Mono.from(idleListener.reactiveEvent(expunged(3))).block();

        assertThat(responses.getLast())
            .isInstanceOfSatisfying(StatusResponse.class, response -> assertThat(response.getTag()).isEqualTo(TAG));
        assertThat(selected.uid(4)).contains(MessageUid.of(4));
        assertThat(selected.expungedUids()).containsExactly(MessageUid.of(3));
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
}
