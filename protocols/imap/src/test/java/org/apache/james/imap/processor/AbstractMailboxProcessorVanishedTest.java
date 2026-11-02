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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import jakarta.mail.Flags;

import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.response.VanishedResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class AbstractMailboxProcessorVanishedTest {
    private static final MessageUid UID_NEXT = MessageUid.of(39);
    private static final List<MessageRange> REQUESTED_RANGES = List.of(MessageRange.range(MessageUid.of(1), MessageUid.of(100)));

    private CheckProcessor processor;
    private SelectedMailbox selectedMailbox;
    private MessageManager mailbox;
    private MailboxSession mailboxSession;
    private List<ImapResponseMessage> responses;
    private ImapProcessor.Responder responder;

    @BeforeEach
    void setUp() {
        processor = new CheckProcessor(mock(MailboxManager.class), mock(StatusResponseFactory.class), new RecordingMetricFactory());
        selectedMailbox = mock(SelectedMailbox.class);
        mailbox = mock(MessageManager.class);
        mailboxSession = mock(MailboxSession.class);
        responses = new ArrayList<>();
        responder = mock(ImapProcessor.Responder.class);
        doAnswer(invocation -> responses.add(invocation.getArgument(0)))
            .when(responder).respond(any());
    }

    private void sessionKnows(List<MessageUid> uids) {
        when(selectedMailbox.allUids()).thenReturn(uids);
        when(selectedMailbox.getLastUid()).thenReturn(uids.stream().max(MessageUid::compareTo));
    }

    private void storeHoldsAboveTheSessionLastUid(MessageRange queriedRange, MessageUid... uids) {
        when(mailbox.listMessagesMetadata(eq(queriedRange), eq(mailboxSession)))
            .thenReturn(Flux.fromArray(uids).map(uid -> new ComposedMessageIdWithMetaData(
                new ComposedMessageId(TestId.of(1), TestMessageId.of(uid.asLong()), uid),
                new Flags(), ModSeq.first(), ThreadId.fromBaseMessageId(TestMessageId.of(uid.asLong())))));
    }

    private static List<MessageUid> uids(long from, long to) {
        return LongStream.rangeClosed(from, to).mapToObj(MessageUid::of).collect(Collectors.toList());
    }

    private Optional<String> vanished() {
        processor.respondVanished(selectedMailbox, REQUESTED_RANGES, UID_NEXT, mailbox, mailboxSession, responder).block();
        return responses.stream()
            .filter(VanishedResponse.class::isInstance)
            .map(VanishedResponse.class::cast)
            .map(response -> UidRange.toString(response.getUids()))
            .findFirst();
    }

    @Test
    void storedMessagesUnknownBySessionShouldNotBeReportedAsVanished() {
        // UID 38 is stored, but its event did not reach the session yet
        sessionKnows(uids(1, 37));
        storeHoldsAboveTheSessionLastUid(MessageRange.range(MessageUid.of(38), MessageUid.of(38)), MessageUid.of(38));

        assertThat(vanished()).isEmpty();
    }

    @Test
    void messagesExpungedAboveTheSessionLastUidShouldBeReportedAsVanished() {
        // UID 38 was appended then expunged before the session knew about it
        sessionKnows(uids(1, 37));
        storeHoldsAboveTheSessionLastUid(MessageRange.range(MessageUid.of(38), MessageUid.of(38)));

        assertThat(vanished()).contains(UidRange.toString(new UidRange[] {new UidRange(MessageUid.of(38))}));
    }

    @Test
    void expungedMessagesKnownBySessionShouldStillBeReportedWhenMessagesAreArriving() {
        List<MessageUid> knownUids = new ArrayList<>(uids(1, 37));
        knownUids.remove(MessageUid.of(5));
        sessionKnows(knownUids);
        storeHoldsAboveTheSessionLastUid(MessageRange.range(MessageUid.of(38), MessageUid.of(38)), MessageUid.of(38));

        assertThat(vanished()).contains(UidRange.toString(new UidRange[] {new UidRange(MessageUid.of(5))}));
    }

    @Test
    void storeShouldNotBeQueriedWhenTheSessionKnowsEveryAssignedUid() {
        sessionKnows(uids(1, 38));

        // No stubbing of listMessagesMetadata: a call would return null and fail
        assertThat(vanished()).isEmpty();
    }
}
