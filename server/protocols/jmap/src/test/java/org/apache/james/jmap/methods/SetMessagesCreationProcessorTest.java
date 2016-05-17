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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.mailet.Mail;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesCreationProcessorTest {

    private static final Message FAKE_MESSAGE = Message.builder()
            .id(MessageId.of("user|outbox|1"))
            .blobId("anything")
            .threadId("anything")
            .mailboxIds(ImmutableList.of("mailboxId"))
            .headers(ImmutableMap.of())
            .subject("anything")
            .size(0)
            .date(ZonedDateTime.now())
            .preview("anything")
            .build();

    @Test
    public void processShouldReturnEmptyCreatedWhenRequestHasEmptyCreate() {
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null, null, null, null, null) {
            @Override
            protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
				Mailbox fakeOutbox = (Mailbox) mock(Mailbox.class);
                when(fakeOutbox.getName()).thenReturn("outbox");
                return Optional.of(fakeOutbox);
            }
        };
        SetMessagesRequest requestWithEmptyCreate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyCreate, buildStubbedSession());

        assertThat(result.getCreated()).isEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    private MailboxSession buildStubbedSession() {
        MailboxSession.User stubUser = mock(MailboxSession.User.class);
        when(stubUser.getUserName()).thenReturn("alice@example.com");
        MailboxSession stubSession = mock(MailboxSession.class);
        when(stubSession.getPathDelimiter()).thenReturn('.');
        when(stubSession.getUser()).thenReturn(stubUser);
        when(stubSession.getPersonalSpace()).thenReturn("#private");
        return stubSession;
    }

    @Test
    public void processShouldReturnNonEmptyCreatedWhenRequestHasNonEmptyCreate() throws MailboxException {
        // Given
        MessageMapper stubMapper = mock(MessageMapper.class);
        MailboxSessionMapperFactory mockSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(mockSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(stubMapper);

        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null, mockSessionMapperFactory, null, null, null) {
            @Override
            protected MessageWithId<Message> createMessageInOutboxAndSend(MessageWithId.CreationMessageEntry createdEntry, MailboxSession session, Mailbox outbox, Function<Long, MessageId> buildMessageIdFromUid) {
                return new MessageWithId<>(createdEntry.getCreationId(), FAKE_MESSAGE);
            }
            @Override
            protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
                Mailbox fakeOutbox = mock(Mailbox.class);
                when(fakeOutbox.getName()).thenReturn("outbox");
                return Optional.of(fakeOutbox);
            }
        };
        // When
        SetMessagesResponse result = sut.process(buildFakeCreationRequest(), buildStubbedSession());

        // Then
        assertThat(result.getCreated()).isNotEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test(expected = MailboxRoleNotFoundException.class)
    public void processShouldThrowWhenOutboxNotFound() {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null, null, null, null, null) {
            @Override
            protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
                return Optional.empty();
            }
        };
        // When
        sut.process(buildFakeCreationRequest(), null);
    }

    @Test
    public void processShouldCallMessageMapperWhenRequestHasNonEmptyCreate() throws MailboxException {
        // Given
        Mailbox fakeOutbox = mock(Mailbox.class);
        MessageMapper mockMapper = mock(MessageMapper.class);
        MailboxSessionMapperFactory stubSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(stubSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(mockMapper);
        MailSpool mockedMailSpool = mock(MailSpool.class);
        MailFactory mockedMailFactory = mock(MailFactory.class);

        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null,
                stubSessionMapperFactory, new MIMEMessageConverter(), mockedMailSpool, mockedMailFactory) {
            @Override
            protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
                TestId stubMailboxId = mock(TestId.class);
                when(stubMailboxId.serialize()).thenReturn("user|outbox|12345");
                when(fakeOutbox.getMailboxId()).thenReturn(stubMailboxId);
                when(fakeOutbox.getName()).thenReturn("outbox");
                return Optional.of(fakeOutbox);
            }
        };
        // When
        sut.process(buildFakeCreationRequest(), buildStubbedSession());

        // Then
        verify(mockMapper).add(eq(fakeOutbox), any(MailboxMessage.class));
    }

    @Test
    public void processShouldSendMailWhenRequestHasNonEmptyCreate() throws Exception {
        // Given
        Mailbox fakeOutbox = mock(Mailbox.class);
        MessageMapper mockMapper = mock(MessageMapper.class);
        MailboxSessionMapperFactory stubSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(stubSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(mockMapper);
        MailSpool mockedMailSpool = mock(MailSpool.class);
        MailFactory mockedMailFactory = mock(MailFactory.class);

        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(null, null,
                stubSessionMapperFactory, new MIMEMessageConverter(), mockedMailSpool, mockedMailFactory) {
            @Override
            protected Optional<Mailbox> getOutbox(MailboxSession session) throws MailboxException {
                TestId stubMailboxId = mock(TestId.class);
                when(stubMailboxId.serialize()).thenReturn("user|outbox|12345");
                when(fakeOutbox.getMailboxId()).thenReturn(stubMailboxId);
                when(fakeOutbox.getName()).thenReturn("outbox");
                return Optional.of(fakeOutbox);
            }
        };
        // When
        sut.process(buildFakeCreationRequest(), buildStubbedSession());

        // Then
        verify(mockedMailSpool).send(any(Mail.class), any(MailMetadata.class));
    }

    private SetMessagesRequest buildFakeCreationRequest() {
        return SetMessagesRequest.builder()
                .create(ImmutableMap.of(CreationMessageId.of("anything-really"), CreationMessage.builder()
                    .from(DraftEmailer.builder().name("alice").email("alice@example.com").build())
                    .to(ImmutableList.of(DraftEmailer.builder().name("bob").email("bob@example.com").build()))
                    .subject("Hey! ")
                    .mailboxIds(ImmutableList.of("mailboxId"))
                    .build()
                ))
                .build();
    }
}