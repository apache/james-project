/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public abstract class ThreadIdGuessingAlgorithmContract {
    public static final Username USER = Username.of("quan");

    protected EventBus eventBus;
    protected MessageId.Factory messageIdFactory;
    protected ThreadIdGuessingAlgorithm testee;
    protected MessageId newBasedMessageId;
    protected MailboxSession mailboxSession;
    private MailboxManager mailboxManager;
    private MessageManager inbox;
    private MessageMapper messageMapper;
    private CombinationManagerTestSystem testingData;
    private MessageId otherBasedMessageId;
    private Mailbox mailbox;

    protected abstract CombinationManagerTestSystem createTestingData();

    protected abstract ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData);

    protected abstract MessageMapper createMessageMapper(MailboxSession mailboxSession);

    protected abstract MessageId initNewBasedMessageId();

    protected abstract MessageId initOtherBasedMessageId();

    protected abstract Flux<Void> saveThreadData(Username username, Set<MimeMessageId> mimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Subject> baseSubject);

    @BeforeEach
    void setUp() throws Exception {
        testingData = createTestingData();
        testee = initThreadIdGuessingAlgorithm(testingData);
        newBasedMessageId = initNewBasedMessageId();
        otherBasedMessageId = initOtherBasedMessageId();

        mailboxManager = testingData.getMailboxManager();
        mailboxSession = mailboxManager.createSystemSession(USER);
        mailboxManager.createMailbox(MailboxPath.inbox(USER), mailboxSession);
        messageMapper = createMessageMapper(mailboxSession);
        inbox = mailboxManager.getMailbox(MailboxPath.inbox(USER), mailboxSession);
        mailbox = inbox.getMailboxEntity();
    }

    @Test
    void givenNonMailWhenAddAMailThenGuessingThreadIdShouldBasedOnGeneratedMessageId() {
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, Optional.of(new MimeMessageId("abc")), Optional.empty(), Optional.empty(), Optional.of(new Subject("test")), mailboxSession).block();

        assertThat(threadId.getBaseMessageId()).isEqualTo(newBasedMessageId);
    }

    private static Stream<Arguments> givenOldMailWhenAddNewRelatedMailsThenGuessingThreadIdShouldReturnSameThreadIdWithOldMail() {
        return Stream.of(
            // mails related to old message by subject and Message-ID (but this should not happen in real world cause every mail should have an unique MimeMessageId)
            Arguments.of(Optional.of(new MimeMessageId("Message-ID")), Optional.empty(), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.of(new MimeMessageId("someInReplyTo")), Optional.empty(), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.of(new MimeMessageId("references1")), Optional.empty(), Optional.empty(), Optional.of(new Subject("Re: Test"))),

            // mails related to old message by subject and In-Reply-To
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("Message-ID")), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("someInReplyTo")), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("references2")), Optional.empty(), Optional.of(new Subject("Fwd: Re: Test"))),

            // mails related to old message by subject and References
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("Message-ID"))), Optional.of(new Subject("Fwd: Re: Test"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("someInReplyTo"))), Optional.of(new Subject("Fwd: Re: Test"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("NonRelated-references2"))), Optional.of(new Subject("Fwd: Re: Test"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("NonRelated-references1"), new MimeMessageId("references2"))), Optional.of(new Subject("Fwd: Re: Test"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))), Optional.of(new Subject("Fwd: Re: Test")))
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenOldMailWhenAddNewRelatedMailsThenGuessingThreadIdShouldReturnSameThreadIdWithOldMail(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) throws Exception {
        // given old mail
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).collectList().block();

        // add new related mails
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, mimeMessageId, inReplyTo, references, subject, mailboxSession).block();

        // guessing threadId should return same threadId with old mail
        assertThat(threadId).isEqualTo(message.getThreadId());
    }

    private static Stream<Arguments> givenOldMailWhenAddNewMailsWithRelatedSubjectButHaveNonIdenticalMessageIDThenGuessingThreadIdShouldBasedOnGeneratedMessageId() {
        return Stream.of(
            // mails related to old message by subject but have non same identical Message-ID
            Arguments.of(Optional.of(new MimeMessageId("NonRelated-Message-ID")), Optional.empty(), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("NonRelated-someInReplyTo")), Optional.empty(), Optional.of(new Subject("Re: Test"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("NonRelated-references1"), new MimeMessageId("NonRelated-references2"))), Optional.of(new Subject("Re: Test")))
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenOldMailWhenAddNewMailsWithRelatedSubjectButHaveNonIdenticalMessageIDThenGuessingThreadIdShouldBasedOnGeneratedMessageId(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) throws Exception {
        // given old mail
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).collectList().block();

        // add mails related to old message by subject but have non same identical Message-ID
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, mimeMessageId, inReplyTo, references, subject, mailboxSession).block();

        // guessing threadId should based on generated MessageId
        assertThat(threadId.getBaseMessageId()).isEqualTo(newBasedMessageId);
    }

    private static Stream<Arguments> givenOldMailWhenAddNewMailsWithNonRelatedSubjectButHaveSameIdenticalMessageIDThenGuessingThreadIdShouldBasedOnGeneratedMessageId() {
        return Stream.of(
            // mails related to old message by having identical Message-ID but non related subject
            Arguments.of(Optional.of(new MimeMessageId("Message-ID")), Optional.empty(), Optional.empty(), Optional.of(new Subject("NonRelated-Subject"))),
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("someInReplyTo")), Optional.empty(), Optional.of(new Subject("NonRelated-Subject"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))), Optional.of(new Subject("NonRelated-Subject")))
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenOldMailWhenAddNewMailsWithNonRelatedSubjectButHaveSameIdenticalMessageIDThenGuessingThreadIdShouldBasedOnGeneratedMessageId(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) throws Exception {
        // given old mail
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).collectList().block();

        // add mails related to old message by having identical Message-ID but non related subject
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, mimeMessageId, inReplyTo, references, subject, mailboxSession).block();

        // guess ThreadId should based on generated MessageId
        assertThat(threadId.getBaseMessageId()).isEqualTo(newBasedMessageId);
    }

    private static Stream<Arguments> givenOldMailWhenAddNonRelatedMailsThenGuessingThreadIdShouldBasedOnGeneratedMessageId() {
        return Stream.of(
            // mails non related to old message by both subject and identical Message-ID
            Arguments.of(Optional.of(new MimeMessageId("NonRelated-Message-ID")), Optional.empty(), Optional.empty(), Optional.of(new Subject("NonRelated-Subject"))),
            Arguments.of(Optional.empty(), Optional.of(new MimeMessageId("NonRelated-someInReplyTo")), Optional.empty(), Optional.of(new Subject("NonRelated-Subject"))),
            Arguments.of(Optional.empty(), Optional.empty(), Optional.of(List.of(new MimeMessageId("NonRelated-references1"), new MimeMessageId("NonRelated-references2"))), Optional.of(new Subject("NonRelated-Subject")))
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenOldMailWhenAddNonRelatedMailsThenGuessingThreadIdShouldBasedOnGeneratedMessageId(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) throws Exception {
        // given old mail
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

        Set<MimeMessageId> mimeMessageIds = buildMimeMessageIdSet(Optional.of(new MimeMessageId("Message-ID")),
            Optional.of(new MimeMessageId("someInReplyTo")),
            Optional.of(List.of(new MimeMessageId("references1"), new MimeMessageId("references2"))));
        saveThreadData(mailboxSession.getUser(), mimeMessageIds, message.getId().getMessageId(), message.getThreadId(), Optional.of(new Subject("Test"))).collectList().block();

        // add mails non related to old message by both subject and identical Message-ID
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, mimeMessageId, inReplyTo, references, subject, mailboxSession).block();

        // guess ThreadId should based on generatedMessageId
        assertThat(threadId.getBaseMessageId()).isEqualTo(newBasedMessageId);
    }

    @Test
    void givenThreeMailsInAThreadThenGetThreadShouldReturnAListWithThreeMessageIdsSortedByArrivalDate() throws MailboxException {
        MailboxMessage message1 = createMessage(mailbox, ThreadId.fromBaseMessageId(newBasedMessageId));
        MailboxMessage message2 = createMessage(mailbox, ThreadId.fromBaseMessageId(newBasedMessageId));
        MailboxMessage message3 = createMessage(mailbox, ThreadId.fromBaseMessageId(newBasedMessageId));

        appendMessageThenDispatchAddedEvent(mailbox, message1);
        appendMessageThenDispatchAddedEvent(mailbox, message2);
        appendMessageThenDispatchAddedEvent(mailbox, message3);

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession);

        assertThat(messageIds.collectList().block())
            .isEqualTo(ImmutableList.of(message1.getMessageId(), message2.getMessageId(), message3.getMessageId()));
    }

    @Test
    void givenNonMailInAThreadThenGetThreadShouldThrowThreadNotFoundException() {
        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession);

        assertThatThrownBy(() -> testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession).collectList().block())
            .getCause()
            .isInstanceOf(ThreadNotFoundException.class);
    }

    @Test
    void givenAMailInAThreadThenGetThreadShouldReturnAListWithOnlyOneMessageIdInThatThread() throws MailboxException {
        MailboxMessage message1 = createMessage(mailbox, ThreadId.fromBaseMessageId(newBasedMessageId));

        appendMessageThenDispatchAddedEvent(mailbox, message1);

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession);

        assertThat(messageIds.collectList().block())
            .containsOnly(message1.getMessageId());
    }

    @Test
    void givenTwoDistinctThreadsThenGetThreadShouldNotReturnUnrelatedMails() throws MailboxException {
        // given message1 and message2 in thread1, message3 in thread2
        ThreadId threadId1 = ThreadId.fromBaseMessageId(newBasedMessageId);
        ThreadId threadId2 = ThreadId.fromBaseMessageId(otherBasedMessageId);
        MailboxMessage message1 = createMessage(mailbox, threadId1);
        MailboxMessage message2 = createMessage(mailbox, threadId1);
        MailboxMessage message3 = createMessage(mailbox, threadId2);

        appendMessageThenDispatchAddedEvent(mailbox, message1);
        appendMessageThenDispatchAddedEvent(mailbox, message2);
        appendMessageThenDispatchAddedEvent(mailbox, message3);

        // then get thread2 should not return unrelated message1 and message2
        Flux<MessageId> messageIds = testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(otherBasedMessageId), mailboxSession);

        assertThat(messageIds.collectList().block())
            .doesNotContain(message1.getMessageId(), message2.getMessageId());
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, ThreadId threadId) {
        MessageId messageId = messageIdFactory.generate();
        String content = "Some content";
        int bodyStart = 12;
        return new SimpleMailboxMessage(messageId,
            threadId,
            new Date(),
            content.length(),
            bodyStart,
            new ByteContent(content.getBytes()),
            new Flags(),
            new PropertyBuilder().build(),
            mailbox.getMailboxId());
    }

    private void appendMessageThenDispatchAddedEvent(Mailbox mailbox, MailboxMessage mailboxMessage) throws MailboxException {
        MessageMetaData messageMetaData = messageMapper.add(mailbox, mailboxMessage);
        eventBus.dispatch(EventFactory.added()
                .randomEventId()
                .mailboxSession(mailboxSession)
                .mailbox(mailbox)
                .addMetaData(messageMetaData)
                .isDelivery(!IS_DELIVERY)
                .isAppended(IS_APPENDED)
                .build(),
            new MailboxIdRegistrationKey(mailbox.getMailboxId())).block();
    }

    protected Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }
}
