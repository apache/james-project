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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
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

public abstract class ThreadIdGuessingAlgorithmContract {
    public static final Username USER = Username.of("quan");
    private MailboxManager mailboxManager;
    private MessageManager inbox;
    private ThreadIdGuessingAlgorithm testee;
    private MailboxSession mailboxSession;
    private CombinationManagerTestSystem testingData;
    private MessageId newBasedMessageId;

    protected abstract CombinationManagerTestSystem createTestingData();

    protected abstract ThreadIdGuessingAlgorithm initThreadIdGuessingAlgorithm(CombinationManagerTestSystem testingData);

    protected abstract MessageId initNewBasedMessageId();

    @BeforeEach
    void setUp() throws Exception {
        testingData = createTestingData();
        testee = initThreadIdGuessingAlgorithm(testingData);
        newBasedMessageId = initNewBasedMessageId();

        mailboxManager = testingData.getMailboxManager();
        mailboxSession = mailboxManager.createSystemSession(USER);
        mailboxManager.createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = mailboxManager.getMailbox(MailboxPath.inbox(USER), mailboxSession);
    }

    @Test
    void givenNonMailWhenAddAMailThenGuessingThreadIdShouldBasedOnGeneratedMessageId() throws Exception {
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
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
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
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
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
        MessageManager.AppendResult message = inbox.appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
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

}
