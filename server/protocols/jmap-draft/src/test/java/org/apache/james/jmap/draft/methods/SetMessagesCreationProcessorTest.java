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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.draft.exceptions.MailboxNotOwnedException;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.draft.model.CreationMessageId;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.jmap.draft.send.MailMetadata;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.Mail;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetMessagesCreationProcessorTest {
    private static final Username USER = Username.of("user@example.com");
    private static final Username OTHER_USER = Username.of("other@example.com");
    private static final String OUTBOX = "outbox";
    private static final InMemoryId OUTBOX_ID = InMemoryId.of(12345);
    private static final String DRAFTS = "drafts";
    private static final InMemoryId DRAFTS_ID = InMemoryId.of(12);
    private static final Long TEST_MESSAGE_SIZE = 1L;

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

    private MessageFullViewFactory messageFullViewFactory;
    private MailSpool mockedMailSpool;
    private SystemMailboxesProvider fakeSystemMailboxesProvider;
    private MailboxSession session;
    private AttachmentManager mockedAttachmentManager;
    private MailboxManager mockedMailboxManager;
    private Factory mockedMailboxIdFactory;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private CanSendFrom canSendFrom;
    private SetMessagesCreationProcessor sut;
    private MessageManager outbox;
    private MessageManager drafts;
    private Optional<MessageManager> optionalOutbox;
    private Optional<MessageManager> optionalDrafts;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private MessageAppender messageAppender;
    private MessageSender messageSender;
    private ReferenceUpdater referenceUpdater;

    @Before
    public void setUp() throws MailboxException, DomainListException, UnknownHostException, ConfigurationException {
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        BlobManager blobManager = mock(BlobManager.class);
        when(blobManager.retrieve(any(Collection.class), any())).thenReturn(Flux.empty());
        MessageIdManager messageIdManager = mock(MessageIdManager.class);
        recipientRewriteTable = new MemoryRecipientRewriteTable();

        MemoryDomainList domainList = new MemoryDomainList();
        domainList.addDomain(Domain.of("example.com"));
        domainList.addDomain(Domain.of("other.org"));

        recipientRewriteTable.setUsersRepository(MemoryUsersRepository.withVirtualHosting(domainList));
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(recipientRewriteTable);
        canSendFrom = new CanSendFromImpl(recipientRewriteTable, aliasReverseResolver);
        messageFullViewFactory = new MessageFullViewFactory(blobManager, messageContentExtractor, htmlTextExtractor,
            messageIdManager,
            new MemoryMessageFastViewProjection(new RecordingMetricFactory()));
        mockedMailSpool = mock(MailSpool.class);
        when(mockedMailSpool.send(any(), any())).thenReturn(Mono.empty());
        mockedAttachmentManager = mock(AttachmentManager.class);
        when(mockedAttachmentManager.getAttachments(any(), any())).thenReturn(ImmutableList.of());
        mockedMailboxManager = mock(MailboxManager.class);
        mockedMailboxIdFactory = mock(Factory.class);
        MessageIdManager mockMessageIdManager = mock(MessageIdManager.class);
        
        fakeSystemMailboxesProvider = new TestSystemMailboxesProvider(() -> optionalOutbox, () -> optionalDrafts);
        session = MailboxSessionUtil.create(USER);
        MIMEMessageConverter mimeMessageConverter = new MIMEMessageConverter();
        messageAppender = new MessageAppender(mockedMailboxManager, mockMessageIdManager, mimeMessageConverter, blobManager, JMAPConfiguration.builder().enable().build());
        messageSender = new MessageSender(mockedMailSpool);
        referenceUpdater = new ReferenceUpdater(mockMessageIdManager, mockedMailboxManager);
        sut = new SetMessagesCreationProcessor(messageFullViewFactory,
            fakeSystemMailboxesProvider,
            new RecordingMetricFactory(),
            mockedMailboxManager,
            mockedMailboxIdFactory,
            messageAppender,
            messageSender,
            referenceUpdater,
            canSendFrom);
        
        outbox = mock(MessageManager.class);
        when(mockedMailboxIdFactory.fromString(OUTBOX_ID.serialize()))
            .thenReturn(OUTBOX_ID);
        when(mockedMailboxManager.getMailbox(OUTBOX_ID, session))
            .thenReturn(outbox);
        when(mockedMailboxManager.getMailboxReactive(OUTBOX_ID, session))
            .thenReturn(Mono.just(outbox));
        
        when(outbox.getId()).thenReturn(OUTBOX_ID);
        when(outbox.getMailboxPath()).thenReturn(MailboxPath.forUser(USER, OUTBOX));
        
        when(outbox.appendMessageReactive(any(MessageManager.AppendCommand.class), any(MailboxSession.class)))
            .thenReturn(Mono.just(new MessageManager.AppendResult(new ComposedMessageId(OUTBOX_ID, TestMessageId.of(23), MessageUid.of(1)), TEST_MESSAGE_SIZE,
                Optional.of(ImmutableList.of()), ThreadId.fromBaseMessageId(TestMessageId.of(23)))));

        drafts = mock(MessageManager.class);
        when(drafts.getId()).thenReturn(DRAFTS_ID);
        when(drafts.getMailboxPath()).thenReturn(MailboxPath.forUser(USER, DRAFTS));
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
        sut = new SetMessagesCreationProcessor(messageFullViewFactory, fakeSystemMailboxesProvider, new RecordingMetricFactory(), mockedMailboxManager, mockedMailboxIdFactory, messageAppender, messageSender, referenceUpdater, canSendFrom);

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
        SetMessagesCreationProcessor sut = new SetMessagesCreationProcessor(messageFullViewFactory, doNotProvideOutbox,
            new RecordingMetricFactory(), mockedMailboxManager, mockedMailboxIdFactory,
            messageAppender,
            messageSender,
            referenceUpdater,
            canSendFrom);
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
        verify(outbox).appendMessageReactive(any(MessageManager.AppendCommand.class), any(MailboxSession.class));
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
        when(mockedMailboxManager.getMailbox(any(MailboxId.class), any()))
            .thenReturn(outbox);
        when(mockedMailboxIdFactory.fromString(anyString())).thenReturn(OUTBOX_ID);

        sut.process(notInOutboxCreationRequest, session);

        // Then
        verify(mockedMailSpool, never()).send(any(Mail.class), any(MailMetadata.class));
    }

    @Test
    public void processShouldNotSendWhenSavingToDrafts() throws Exception {
        // When
        CreationMessageId creationMessageId = CreationMessageId.of("anything-really");
        SetMessagesRequest createMessageInDrafts = SetMessagesRequest.builder()
                .create(
                        creationMessageId, creationMessageBuilder.mailboxId(DRAFTS_ID.serialize()).build())
                .build();
        when(mockedMailboxManager.getMailbox(any(MailboxId.class), any()))
            .thenReturn(drafts);
        when(mockedMailboxManager.getMailboxReactive(any(MailboxId.class), any()))
            .thenReturn(Mono.just(drafts));
        when(mockedMailboxIdFactory.fromString(anyString())).thenReturn(DRAFTS_ID);
        
        sut.process(createMessageInDrafts, session);

        // Then
        verify(mockedMailSpool, never()).send(any(Mail.class), any(MailMetadata.class));
    }

    @Test
    public void validateIsUserOwnerOfMailboxesShouldThrowWhenMailboxIdDoesntExist() throws Exception {
        InMemoryId mailboxId = InMemoryId.of(6789);
        when(mockedMailboxManager.getMailbox(mailboxId, session))
            .thenThrow(new MailboxNotFoundException(mailboxId));
        when(mockedMailboxIdFactory.fromString(mailboxId.serialize()))
            .thenReturn(mailboxId);

        assertThatThrownBy(() -> sut.assertIsUserOwnerOfMailboxes(ImmutableList.of(mailboxId), session).block());
    }

    @Test
    public void assertIsUserOwnerOfMailboxesShouldThrowWhenUserIsNotTheOwnerOfTheMailbox() throws Exception {
        InMemoryId mailboxId = InMemoryId.of(6789);
        MessageManager mailbox = mock(MessageManager.class);
        when(mockedMailboxManager.getMailboxReactive(mailboxId, session))
            .thenReturn(Mono.just(mailbox));
        when(mockedMailboxIdFactory.fromString(mailboxId.serialize()))
            .thenReturn(mailboxId);
        when(mailbox.getMailboxPath())
            .thenReturn(MailboxPath.forUser(Username.of("otheruser@example.com"), mailboxId.serialize()));

        assertThatThrownBy(() -> sut.assertIsUserOwnerOfMailboxes(ImmutableList.of(mailboxId), session).block())
            .hasCauseInstanceOf(MailboxNotOwnedException.class);
    }

    @Test
    public void assertIsUserOwnerOfMailboxesShouldNotThrowWhenUserIsTheOwnerOfTheMailbox() throws Exception {
        InMemoryId mailboxId = InMemoryId.of(6789);
        MessageManager mailbox = mock(MessageManager.class);
        when(mockedMailboxManager.getMailboxReactive(mailboxId, session))
            .thenReturn(Mono.just(mailbox));
        when(mockedMailboxIdFactory.fromString(mailboxId.serialize()))
            .thenReturn(mailboxId);
        when(mailbox.getMailboxPath())
            .thenReturn(MailboxPath.forUser(USER, mailboxId.serialize()));

        sut.assertIsUserOwnerOfMailboxes(ImmutableList.of(mailboxId), session).block();
    }

    @Test
    public void assertUserCanSendFromShouldThrowWhenSenderIsNotTheConnectedUser() {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("other")
            .email("other@example.com")
            .build();

        assertThatThrownBy(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .hasCauseInstanceOf(MailboxSendingNotAllowedException.class);
    }

    @Test
    public void assertUserCanSendFromShouldNotThrowWhenSenderIsTheConnectedUser() {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("user")
            .email(USER.asString())
            .build();

        assertThatCode(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .doesNotThrowAnyException();
    }

    @Test
    public void assertUserCanSendFromShouldThrowWhenSenderIsAnAliasOfAnotherUser() throws Exception {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("alias")
            .email("alias@example.com")
            .build();

        recipientRewriteTable.addAliasMapping(MappingSource.fromUser("alias", "example.com"), OTHER_USER.asString());

        assertThatThrownBy(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .hasCauseInstanceOf(MailboxSendingNotAllowedException.class);
    }

    @Test
    public void assertUserCanSendFromShouldNotThrowWhenSenderIsAnAliasOfTheConnectedUser() throws RecipientRewriteTableException {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("alias")
            .email("alias@example.com")
            .build();

        recipientRewriteTable.addAliasMapping(MappingSource.fromUser("alias", "example.com"), USER.asString());

        assertThatCode(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .doesNotThrowAnyException();
    }

    @Test
    public void assertUserCanSendFromShouldNotThrowWhenSenderIsAnAliasOfTheConnectedUserFromADomainAlias() throws RecipientRewriteTableException {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("user")
            .email("user@other.org")
            .build();

           recipientRewriteTable.addMapping(MappingSource.fromDomain(Domain.of("other.org")), Mapping.domainAlias(Domain.of("example.com")));

        assertThatCode(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .doesNotThrowAnyException();
    }

    @Test
    public void assertUserCanSendFromShouldThrowWhenSenderIsAnAliasOfTheConnectedUserFromAGroupAlias() throws RecipientRewriteTableException {
        DraftEmailer sender = DraftEmailer
            .builder()
            .name("group")
            .email("group@example.com")
            .build();

        recipientRewriteTable.addGroupMapping(MappingSource.fromUser("group", "example.com"), USER.asString());

        assertThatThrownBy(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .hasCauseInstanceOf(MailboxSendingNotAllowedException.class);
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
        public Flux<MessageManager> getMailboxByRole(Role aRole, Username username) {
            if (aRole.equals(Role.OUTBOX)) {
                return Flux.fromStream(outboxSupplier.get().stream());
            } else if (aRole.equals(Role.DRAFTS)) {
                return Flux.fromStream(draftsSupplier.get().stream());
            }
            return Flux.empty();
        }
    }
}
