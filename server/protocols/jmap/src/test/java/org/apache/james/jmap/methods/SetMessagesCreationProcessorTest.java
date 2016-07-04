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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.james.jmap.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.jmap.utils.MailboxBasedHtmlTextExtractor;
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.mailet.Mail;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesCreationProcessorTest {
    
    private MessageFactory messageFactory;

    private static final String USER = "user@example.com";
    private static final String OUTBOX = "outbox";
    private static final TestId OUTBOX_ID = TestId.of(12345);
    private static final String DRAFTS = "drafts";
    private static final TestId DRAFTS_ID = TestId.of(12);
    private static final String OUTBOX_MESSAGE_ID = Joiner.on('|').join(USER, OUTBOX, "12345");
    private static final String NAMESPACE = "#private";
    private static final long UID_VALIDITY = 0l;
    private final Mailbox outbox = new SimpleMailbox(new MailboxPath(NAMESPACE, USER, OUTBOX), UID_VALIDITY, OUTBOX_ID);
    private final Mailbox drafts = new SimpleMailbox(new MailboxPath(NAMESPACE, USER, DRAFTS), UID_VALIDITY, DRAFTS_ID);

    private static final Message FAKE_OUTBOX_MESSAGE = Message.builder()
            .id(MessageId.of(OUTBOX_MESSAGE_ID))
            .blobId("anything")
            .threadId("anything")
            .mailboxId(OUTBOX_ID.serialize())
            .headers(ImmutableMap.of())
            .subject("anything")
            .size(0)
            .date(ZonedDateTime.now())
            .preview("anything")
            .build();
    
    @Before
    public void setup() {
        HtmlTextExtractor htmlTextExtractor = new MailboxBasedHtmlTextExtractor(new DefaultTextExtractor());
        MessagePreviewGenerator messagePreview = new MessagePreviewGenerator(htmlTextExtractor);
        messageFactory = new MessageFactory(messagePreview);
    }

    private final CreationMessage.Builder creationMessageBuilder = CreationMessage.builder()
            .from(DraftEmailer.builder().name("alice").email("alice@example.com").build())
            .to(ImmutableList.of(DraftEmailer.builder().name("bob").email("bob@example.com").build()))
            .subject("Hey! ");

    private final CreationMessageId creationMessageId = CreationMessageId.of("dlkja");
    
    private final SetMessagesRequest createMessageInOutbox = SetMessagesRequest.builder()
            .create(
                    creationMessageId, 
                    creationMessageBuilder
                        .mailboxId(OUTBOX_ID.serialize())
                        .from(DraftEmailer.builder().name("user").email("user@example.com").build())
                        .build())
            .build();

    private final Optional<Mailbox> optionalOutbox = Optional.of(outbox);
    private final Optional<Mailbox> optionalDrafts = Optional.of(drafts);

    private MessageMapper mockMapper;
    private MailboxSessionMapperFactory stubSessionMapperFactory;
    private MailSpool mockedMailSpool;
    private MailFactory mockedMailFactory;
    private SystemMailboxesProvider fakeSystemMailboxesProvider;
    private MockMailboxSession session;
    private MIMEMessageConverter mimeMessageConverter;

    @Before
    public void setUp() throws MailboxException {
        mockMapper = mock(MessageMapper.class);
        stubSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(stubSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(mockMapper);
        mockedMailSpool = mock(MailSpool.class);
        mockedMailFactory = mock(MailFactory.class);

        fakeSystemMailboxesProvider = new TestSystemMailboxesProvider(() -> optionalOutbox, () -> optionalDrafts);
        session = new MockMailboxSession(USER);
        mimeMessageConverter = new MIMEMessageConverter();
    }

    @Test
    public void processShouldReturnEmptyCreatedWhenRequestHasEmptyCreate() {
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);

        SetMessagesRequest requestWithEmptyCreate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyCreate, session);

        assertThat(result.getCreated()).isEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test
    public void processShouldReturnNonEmptyCreatedWhenRequestHasNonEmptyCreate() throws MailboxException {
        // Given
        MessageMapper stubMapper = mock(MessageMapper.class);
        MailboxSessionMapperFactory mockSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        when(mockSessionMapperFactory.createMessageMapper(any(MailboxSession.class)))
                .thenReturn(stubMapper);

        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                mockSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider) {
            @Override
            protected MessageWithId createMessageInOutboxAndSend(ValueWithId.CreationMessageEntry createdEntry, MailboxSession session, Mailbox outbox, Function<Long, MessageId> buildMessageIdFromUid) {
                return new MessageWithId(createdEntry.getCreationId(), FAKE_OUTBOX_MESSAGE);
            }
        };
        // When
        SetMessagesResponse result = sut.process(createMessageInOutbox, session);

        // Then
        assertThat(result.getCreated()).isNotEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Ignore("JAMES-1716 : should report an error")
    @Test
    public void processShouldReturnErrorWhenOutboxNotFound() {
        // Given
        TestSystemMailboxesProvider doNotProvideOutbox = new TestSystemMailboxesProvider(Optional::empty, () -> optionalDrafts);
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, doNotProvideOutbox);
        // When
        SetMessagesResponse actual = sut.process(createMessageInOutbox, session);
        
        assertThat(actual.getNotCreated()).hasSize(1).containsKey(creationMessageId);
        assertThat(actual.getNotCreated().get(creationMessageId).getType()).isEqualTo("invalidProperties");
        assertThat(actual.getNotCreated().get(creationMessageId).getDescription()).contains("target mailbox does not exists");
    }

    @Test
    public void processShouldCallMessageMapperWhenRequestHasNonEmptyCreate() throws MailboxException {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);
        // When
        sut.process(createMessageInOutbox, session);

        // Then
        verify(mockMapper).add(eq(outbox), any(MailboxMessage.class));
    }

    @Test
    public void processShouldSendMailWhenRequestHasNonEmptyCreate() throws Exception {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);
        // When
        sut.process(createMessageInOutbox, session);

        // Then
        verify(mockedMailSpool).send(any(Mail.class), any(MailMetadata.class));
    }

    @Test
    public void processShouldNotSpoolMailWhenNotSavingToOutbox() throws Exception {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);
        // When
        SetMessagesRequest notInOutboxCreationRequest =
                SetMessagesRequest.builder()
                    .create(CreationMessageId.of("anything-really"),
                            creationMessageBuilder.mailboxId("any-id-but-outbox-id")
                        .build())
                    .build();

        sut.process(notInOutboxCreationRequest, session);

        // Then
        verify(mockedMailSpool, never()).send(any(Mail.class), any(MailMetadata.class));
    }

    @Test
    public void processShouldReturnNotImplementedErrorWhenSavingToDrafts() {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);

        CreationMessageId creationMessageId = CreationMessageId.of("anything-really");
        SetMessagesRequest createMessageInDrafts = SetMessagesRequest.builder()
                .create(
                        creationMessageId, creationMessageBuilder.mailboxId(DRAFTS_ID.serialize()).build())
                .build();

        // When
        SetMessagesResponse actual = sut.process(createMessageInDrafts, session);

        // Then
        assertThat(actual.getNotCreated()).hasSize(1).containsEntry(creationMessageId, SetError.builder()
                .type("invalidProperties")
                .properties(MessageProperty.mailboxIds)
                .description("Not yet implemented")
                .build());
    }

    @Test
    public void processShouldNotSendWhenSavingToDrafts() throws Exception {
        // Given
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(
                stubSessionMapperFactory, mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider);
        // When
        CreationMessageId creationMessageId = CreationMessageId.of("anything-really");
        SetMessagesRequest createMessageInDrafts = SetMessagesRequest.builder()
                .create(
                        creationMessageId, creationMessageBuilder.mailboxId(DRAFTS_ID.serialize()).build())
                .build();
        sut.process(createMessageInDrafts, session);

        // Then
        verify(mockedMailSpool, never()).send(any(Mail.class), any(MailMetadata.class));
    }


    public static class TestSystemMailboxesProvider implements SystemMailboxesProvider {

        private final Supplier<Optional<Mailbox>> outboxSupplier;
        private final Supplier<Optional<Mailbox>> draftsSupplier;

        private TestSystemMailboxesProvider(Supplier<Optional<Mailbox>> outboxSupplier,
                                            Supplier<Optional<Mailbox>> draftsSupplier) {
            this.outboxSupplier = outboxSupplier;
            this.draftsSupplier = draftsSupplier;
        }

        public Stream<Mailbox> listMailboxes(Role aRole, MailboxSession session) {
            if (aRole.equals(Role.OUTBOX)) {
                return outboxSupplier.get().map(o -> Stream.of(o)).orElse(Stream.empty());
            } else if (aRole.equals(Role.DRAFTS)) {
                return draftsSupplier.get().map(d -> Stream.of(d)).orElse(Stream.empty());
            }
            return Stream.empty();
        }
    }

}
