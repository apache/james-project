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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.ThreadNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
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

    protected ThreadIdGuessingAlgorithm testee;
    protected MessageId newBasedMessageId;
    protected MessageId otherBasedMessageId;
    protected MailboxSession mailboxSession;
    private MessageManager inbox;

    protected abstract CombinationManagerTestSystem createTestingSystem();

    protected abstract ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData);

    protected abstract MessageId initNewBasedMessageId();

    protected abstract MessageId initOtherBasedMessageId();

    private void overrideThreadIdGuessingAlgorithm(StoreMailboxManager manager, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm) {
        try {
            Field field = StoreMailboxManager.class.getDeclaredField("threadIdGuessingAlgorithm");
            field.setAccessible(true);
            field.set(manager, threadIdGuessingAlgorithm);
        } catch (Exception e) {
            throw new RuntimeException("Failed to override threadIdGuessingAlgorithm", e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        CombinationManagerTestSystem testingSystem = createTestingSystem();
        testee = initThreadIdGuessingAlgorithm(testingSystem);
        // Use reflection to set the actual testee threadIdGuessingAlgorithm in StoreMailboxManager, which was NaiveThreadIdGuessingAlgorithm because of circular dependency
        overrideThreadIdGuessingAlgorithm((StoreMailboxManager) testingSystem.getMailboxManager(), testee);

        newBasedMessageId = initNewBasedMessageId();
        otherBasedMessageId = initOtherBasedMessageId();
        mailboxSession = testingSystem.getMailboxManager().createSystemSession(USER);
        testingSystem.getMailboxManager().createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = testingSystem.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);
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
        inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

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
        inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

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
        inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject("Test")
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);

        // add mails non related to old message by both subject and identical Message-ID
        ThreadId threadId = testee.guessThreadIdReactive(newBasedMessageId, mimeMessageId, inReplyTo, references, subject, mailboxSession).block();

        // guess ThreadId should based on generatedMessageId
        assertThat(threadId.getBaseMessageId()).isEqualTo(newBasedMessageId);
    }

    @Test
    void givenThreeMailsInAThreadThenGetThreadShouldReturnAListWithThreeMessageIdsSortedByArrivalDate() throws Exception {
        MessageManager.AppendResult message1 = appendMessage(inbox, new Subject(newBasedMessageId.serialize()));
        MessageManager.AppendResult message2 = appendMessage(inbox, new Subject(newBasedMessageId.serialize()));
        MessageManager.AppendResult message3 = appendMessage(inbox, new Subject(newBasedMessageId.serialize()));

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(message1.getThreadId(), mailboxSession);

        assertThat(messageIds.collectList().block())
            .isEqualTo(ImmutableList.of(message1.getId().getMessageId(),
                message2.getId().getMessageId(),
                message3.getId().getMessageId()));
    }

    @Test
    void givenNonMailInAThreadThenGetThreadShouldThrowThreadNotFoundException() {
        assertThatThrownBy(() -> testee.getMessageIdsInThread(ThreadId.fromBaseMessageId(newBasedMessageId), mailboxSession).collectList().block())
            .getCause()
            .isInstanceOf(ThreadNotFoundException.class);
    }

    @Test
    void givenAMailInAThreadThenGetThreadShouldReturnAListWithOnlyOneMessageIdInThatThread() throws Exception {
        MessageManager.AppendResult message1 = appendMessage(inbox, new Subject(newBasedMessageId.serialize()));

        Flux<MessageId> messageIds = testee.getMessageIdsInThread(message1.getThreadId(), mailboxSession);

        assertThat(messageIds.collectList().block())
            .containsOnly(message1.getId().getMessageId());
    }

    @Test
    void givenTwoDistinctThreadsThenGetThreadShouldNotReturnUnrelatedMails() throws Exception {
        // given message1 and message2 in thread1, message3 in thread2
        Subject subject1 = new Subject(newBasedMessageId.serialize());
        Subject subject2 = new Subject(otherBasedMessageId.serialize());
        MessageManager.AppendResult message1 = appendMessage(inbox, subject1);
        MessageManager.AppendResult message2 = appendMessage(inbox, subject1);
        MessageManager.AppendResult message3 = appendMessage(inbox, subject2);

        // then get thread2 should not return unrelated message1 and message2
        Flux<MessageId> messageIds = testee.getMessageIdsInThread(message3.getThreadId(), mailboxSession);

        assertThat(messageIds.collectList().block())
            .doesNotContain(message1.getId().getMessageId(), message2.getId().getMessageId())
            .containsOnly(message3.getId().getMessageId());
    }

    protected MessageManager.AppendResult appendMessage(MessageManager mailbox, Subject subject) throws Exception {
        return mailbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject(subject.getValue())
            .setMessageId("Message-ID")
            .setField(new RawField("In-Reply-To", "someInReplyTo"))
            .addField(new RawField("References", "references1"))
            .addField(new RawField("References", "references2"))
            .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession);
    }

    protected Set<MimeMessageId> buildMimeMessageIdSet(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references) {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }
}
