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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.sql.Date;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.jmap.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.MessageFactory;
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
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.util.OptionalUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.Mail;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class SetMessagesCreationProcessorTest {
    
    private static final String USER = "user@example.com";
    private static final String OUTBOX = "outbox";
    private static final InMemoryId OUTBOX_ID = InMemoryId.of(12345);
    private static final String DRAFTS = "drafts";
    private static final InMemoryId DRAFTS_ID = InMemoryId.of(12);
    private static final String NAMESPACE = "#private";

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

    private MessageFactory messageFactory;
    private MailSpool mockedMailSpool;
    private MailFactory mockedMailFactory;
    private SystemMailboxesProvider fakeSystemMailboxesProvider;
    private MockMailboxSession session;
    private MIMEMessageConverter mimeMessageConverter;
    private AttachmentManager mockedAttachmentManager;
    private SetMessagesCreationProcessor sut;
    private MessageManager outbox;
    private MessageManager drafts;
    private Optional<MessageManager> optionalOutbox;
    private Optional<MessageManager> optionalDrafts;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Before
    public void setUp() throws MailboxException {
        HtmlTextExtractor htmlTextExtractor = mock(HtmlTextExtractor.class);
        MessagePreviewGenerator messagePreview = mock(MessagePreviewGenerator.class);
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        when(messagePreview.compute(any())).thenReturn("text preview");
        BlobManager blobManager = mock(BlobManager.class);
        when(blobManager.toBlobId(any(MessageId.class))).thenReturn(org.apache.james.mailbox.model.BlobId.fromString("fake"));
        messageFactory = new MessageFactory(blobManager, messagePreview, messageContentExtractor, htmlTextExtractor);
        mockedMailSpool = mock(MailSpool.class);
        mockedMailFactory = mock(MailFactory.class);
        mockedAttachmentManager = mock(AttachmentManager.class);
        
        fakeSystemMailboxesProvider = new TestSystemMailboxesProvider(() -> optionalOutbox, () -> optionalDrafts);
        session = new MockMailboxSession(USER);
        mimeMessageConverter = new MIMEMessageConverter();
        sut = new SetMessagesCreationProcessor(mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider, mockedAttachmentManager, new NoopMetricFactory());
        
        outbox = mock(MessageManager.class);
        when(outbox.getId()).thenReturn(OUTBOX_ID);
        when(outbox.getMailboxPath()).thenReturn(new MailboxPath(NAMESPACE, USER, OUTBOX));
        
        when(outbox.appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), any(Boolean.class), any(Flags.class)))
            .thenReturn(new ComposedMessageId(OUTBOX_ID, TestMessageId.of(23), MessageUid.of(1)));

        drafts = mock(MessageManager.class);
        when(drafts.getId()).thenReturn(DRAFTS_ID);
        when(drafts.getMailboxPath()).thenReturn(new MailboxPath(NAMESPACE, USER, DRAFTS));
        optionalOutbox = Optional.of(outbox);
        optionalDrafts = Optional.of(drafts);
    }

    @Test
    public void processShouldReturnEmptyCreatedWhenRequestHasEmptyCreate() {
        SetMessagesRequest requestWithEmptyCreate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyCreate, session);

        assertThat(result.getCreated()).isEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test
    public void processShouldThrowWhenBothIsFlagAndKeywords() {
        expectedException.expect(IllegalArgumentException.class);
        SetMessagesRequest createMessageWithError = SetMessagesRequest.builder()
            .create(
                creationMessageId,
                creationMessageBuilder
                    .mailboxId(OUTBOX_ID.serialize())
                    .isAnswered(Optional.of(true))
                    .keywords(ImmutableMap.of("$Answered", true))
                    .build())
            .build();

        sut.process(createMessageWithError, session);
    }

    @Test
    public void processShouldCreateWhenKeywords() {
        SetMessagesRequest createMessageWithKeywords = SetMessagesRequest.builder()
            .create(
                creationMessageId,
                creationMessageBuilder
                    .mailboxId(OUTBOX_ID.serialize())
                    .keywords(ImmutableMap.of("$Answered", true))
                    .build())
            .build();

        SetMessagesResponse result = sut.process(createMessageWithKeywords, session);

        assertThat(result.getCreated()).isNotEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test
    public void processShouldCreateWhenIsFlag() {
        SetMessagesRequest createMessageWithKeywords = SetMessagesRequest.builder()
            .create(
                creationMessageId,
                creationMessageBuilder
                    .mailboxId(OUTBOX_ID.serialize())
                    .isAnswered(Optional.of(true))
                    .build())
            .build();

        SetMessagesResponse result = sut.process(createMessageWithKeywords, session);

        assertThat(result.getCreated()).isNotEmpty();
        assertThat(result.getNotCreated()).isEmpty();
    }

    @Test
    public void processShouldReturnNonEmptyCreatedWhenRequestHasNonEmptyCreate() throws MailboxException {
        // Given
        sut = new SetMessagesCreationProcessor(mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, fakeSystemMailboxesProvider, mockedAttachmentManager, new NoopMetricFactory());

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
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(mimeMessageConverter, mockedMailSpool, mockedMailFactory, messageFactory, doNotProvideOutbox, mockedAttachmentManager, new NoopMetricFactory());
        // When
        SetMessagesResponse actual = sut.process(createMessageInOutbox, session);
        
        assertThat(actual.getNotCreated()).hasSize(1).containsKey(creationMessageId);
        assertThat(actual.getNotCreated().get(creationMessageId).getType()).isEqualTo("invalidProperties");
        assertThat(actual.getNotCreated().get(creationMessageId).getDescription()).contains("target mailbox does not exists");
    }

    @Test
    public void processShouldCallAppendMessageWhenRequestHasNonEmptyCreate() throws MailboxException {
        // When
        sut.process(createMessageInOutbox, session);

        // Then
        verify(outbox).appendMessage(any(InputStream.class), any(Date.class), any(MailboxSession.class), any(Boolean.class), any(Flags.class));
    }

    @Test
    public void processShouldSendMailWhenRequestHasNonEmptyCreate() throws Exception {
        // When
        sut.process(createMessageInOutbox, session);

        // Then
        verify(mockedMailSpool).send(any(Mail.class), any(MailMetadata.class));
    }

    @Test
    public void processShouldNotSpoolMailWhenNotSavingToOutbox() throws Exception {
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


    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobId() throws MailboxException {
        BlobId unknownBlobId = BlobId.of("unknownBlobId");
        AttachmentId unknownAttachmentId = AttachmentId.from(unknownBlobId.getRawValue());
        when(mockedAttachmentManager.getAttachment(unknownAttachmentId, session)).thenThrow(new AttachmentNotFoundException(unknownBlobId.getRawValue()));
        
        assertThatThrownBy(() -> sut.assertAttachmentsExist(
                new CreationMessageEntry(
                        creationMessageId, 
                        creationMessageBuilder.attachments(
                                Attachment.builder().size(12l).type("image/jpeg").blobId(unknownBlobId).build())
                            .build()
                        ),
                session))
            .isInstanceOf(AttachmentsNotFoundException.class);
    }
    
    @Test
    public void assertAttachmentsExistShouldThrowWhenUnknownBlobIds() throws MailboxException {
        BlobId unknownBlobId1 = BlobId.of("unknownBlobId1");
        BlobId unknownBlobId2 = BlobId.of("unknownBlobId2");
        AttachmentId unknownAttachmentId1 = AttachmentId.from(unknownBlobId1.getRawValue());
        AttachmentId unknownAttachmentId2 = AttachmentId.from(unknownBlobId2.getRawValue());

        when(mockedAttachmentManager.getAttachment(unknownAttachmentId1, session)).thenThrow(new AttachmentNotFoundException(unknownBlobId1.getRawValue()));
        when(mockedAttachmentManager.getAttachment(unknownAttachmentId2, session)).thenThrow(new AttachmentNotFoundException(unknownBlobId2.getRawValue()));
        
        assertThatThrownBy(() -> sut.assertAttachmentsExist(
                new CreationMessageEntry(
                        creationMessageId, 
                        creationMessageBuilder.attachments(
                                Attachment.builder().size(12l).type("image/jpeg").blobId(unknownBlobId1).build(),
                                Attachment.builder().size(23l).type("image/git").blobId(unknownBlobId2).build())
                            .build()
                        ),
                session))
            .isInstanceOf(AttachmentsNotFoundException.class)
            .matches(e -> ((AttachmentsNotFoundException)e).getAttachmentIds().containsAll(ImmutableSet.of(unknownBlobId1, unknownBlobId2)));
    }
    
    public static class TestSystemMailboxesProvider implements SystemMailboxesProvider {

        private final Supplier<Optional<MessageManager>> outboxSupplier;
        private final Supplier<Optional<MessageManager>> draftsSupplier;

        private TestSystemMailboxesProvider(Supplier<Optional<MessageManager>> outboxSupplier,
                                            Supplier<Optional<MessageManager>> draftsSupplier) {
            this.outboxSupplier = outboxSupplier;
            this.draftsSupplier = draftsSupplier;
        }

        @Override
        public Stream<MessageManager> getMailboxByRole(Role aRole, MailboxSession session) {
            if (aRole.equals(Role.OUTBOX)) {
                return OptionalUtils.toStream(outboxSupplier.get());
            } else if (aRole.equals(Role.DRAFTS)) {
                return OptionalUtils.toStream(draftsSupplier.get());
            }
            return Stream.empty();
        }
    }

}
