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

import static org.apache.james.user.memory.MemoryUsersRepository.withVirtualHosting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.Supplier;

import javax.mail.internet.AddressException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessageId;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.UpdateMessagePatch;
import org.apache.james.jmap.draft.send.MailSpool;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;

public class SetMessagesUpdateProcessorTest {


    private static final Username USER = Username.of("user@example.com");
    private static final Username OTHER_USER = Username.of("other@example.com");
    private static final String OUTBOX = "outbox";
    private static final InMemoryId OUTBOX_ID = InMemoryId.of(12345);
    private static final String DRAFTS = "drafts";
    private static final InMemoryId DRAFTS_ID = InMemoryId.of(12);
    private static final Long TEST_MESSAGE_SIZE = 1L;

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

    private final CreationMessage.Builder creationMessageBuilder = CreationMessage.builder()
        .from(CreationMessage.DraftEmailer.builder().name("alice").email("alice@example.com").build())
        .to(ImmutableList.of(CreationMessage.DraftEmailer.builder().name("bob").email("bob@example.com").build()))
        .subject("Hey! ");

    private final CreationMessageId creationMessageId = CreationMessageId.of("dlkja");


    private MailSpool mockedMailSpool;
    private SystemMailboxesProvider fakeSystemMailboxesProvider;
    private MailboxSession session;
    private MailboxManager mockedMailboxManager;
    private MailboxId.Factory mockedMailboxIdFactory;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private CanSendFrom canSendFrom;
    private SetMessagesUpdateProcessor sut;
    private MessageIdManager mockMessageIdManager;
    private MessageManager outbox;
    private MessageManager drafts;
    private Optional<MessageManager> optionalOutbox;
    private Optional<MessageManager> optionalDrafts;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private MessageSender messageSender;
    private ReferenceUpdater referenceUpdater;

    @Before
    public void setUp() throws MailboxException, DomainListException, UnknownHostException, ConfigurationException {
        MessageIdManager messageIdManager = mock(MessageIdManager.class);
        recipientRewriteTable = new MemoryRecipientRewriteTable();

        MemoryDomainList domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("example.com"));
        domainList.addDomain(Domain.of("other.org"));
        recipientRewriteTable.setUsersRepository(withVirtualHosting(domainList));
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(recipientRewriteTable);
        canSendFrom = new CanSendFromImpl(recipientRewriteTable, aliasReverseResolver);
        mockedMailSpool = mock(MailSpool.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockedMailboxIdFactory = mock(MailboxId.Factory.class);

        mockMessageIdManager = mock(MessageIdManager.class);

        fakeSystemMailboxesProvider = new TestSystemMailboxesProvider(() -> optionalOutbox, () -> optionalDrafts);
        session = MailboxSessionUtil.create(USER);
        messageSender = new MessageSender(mockedMailSpool);
        referenceUpdater = new ReferenceUpdater(mockMessageIdManager, mockedMailboxManager);

        UpdateMessagePatchConverter updateMessagePatchConverter = null;
        MailboxManager mailboxManager = null;
        sut = new SetMessagesUpdateProcessor(updateMessagePatchConverter,
            messageIdManager,
            mailboxManager,
            fakeSystemMailboxesProvider,
            mockedMailboxIdFactory,
            messageSender,
            new RecordingMetricFactory(),
            referenceUpdater,
            canSendFrom);

        outbox = mock(MessageManager.class);
        when(mockedMailboxIdFactory.fromString(OUTBOX_ID.serialize()))
            .thenReturn(OUTBOX_ID);
        when(mockedMailboxManager.getMailbox(OUTBOX_ID, session))
            .thenReturn(outbox);

        when(outbox.getId()).thenReturn(OUTBOX_ID);
        when(outbox.getMailboxPath()).thenReturn(MailboxPath.forUser(USER, OUTBOX));

        when(outbox.appendMessage(any(MessageManager.AppendCommand.class), any(MailboxSession.class)))
            .thenReturn(new MessageManager.AppendResult(
                new ComposedMessageId(OUTBOX_ID, TestMessageId.of(23), MessageUid.of(1)), TEST_MESSAGE_SIZE,
                Optional.empty(), ThreadId.fromBaseMessageId(TestMessageId.of(23))));

        drafts = mock(MessageManager.class);
        when(drafts.getId()).thenReturn(DRAFTS_ID);
        when(drafts.getMailboxPath()).thenReturn(MailboxPath.forUser(USER, DRAFTS));
        optionalOutbox = Optional.of(outbox);
        optionalDrafts = Optional.of(drafts);
    }

    @Test
    public void processShouldReturnEmptyUpdatedWhenRequestHasEmptyUpdate() {
        SetMessagesRequest requestWithEmptyUpdate = SetMessagesRequest.builder().build();

        SetMessagesResponse result = sut.process(requestWithEmptyUpdate, session);

        assertThat(result.getUpdated()).isEmpty();
        assertThat(result.getNotUpdated()).isEmpty();
    }

    @Test
    public void processShouldReturnNonEmptyNotUpdatedWhenRequestHasInvalidUpdate() {
        // Given
        UpdateMessagePatchConverter mockConverter = mock(UpdateMessagePatchConverter.class);
        UpdateMessagePatch mockInvalidPatch = mock(UpdateMessagePatch.class);
        when(mockInvalidPatch.isValid()).thenReturn(false);

        MessageProperties.MessageProperty invalidProperty = MessageProperties.MessageProperty.from;
        ImmutableList<ValidationResult> nonEmptyValidationResult = ImmutableList.of(ValidationResult.builder()
                .property(invalidProperty.toString()).build());
        when(mockInvalidPatch.getValidationErrors())
                .thenReturn(nonEmptyValidationResult);
        when(mockConverter.fromJsonNode(any(ObjectNode.class)))
                .thenReturn(mockInvalidPatch);


        MailboxManager mailboxManager = null;
        SetMessagesUpdateProcessor sut = new SetMessagesUpdateProcessor(mockConverter,
            mockMessageIdManager,
            mailboxManager,
            fakeSystemMailboxesProvider,
            mockedMailboxIdFactory,
            messageSender,
            new RecordingMetricFactory(),
            referenceUpdater,
            canSendFrom);
        MessageId requestMessageId = TestMessageId.of(1);
        SetMessagesRequest requestWithInvalidUpdate = SetMessagesRequest.builder()
                .update(ImmutableMap.of(requestMessageId, JsonNodeFactory.instance.objectNode()))
                .build();

        // When
        SetMessagesResponse result = sut.process(requestWithInvalidUpdate, session);

        // Then
        assertThat(result.getNotUpdated()).describedAs("NotUpdated should not be empty").isNotEmpty();
        assertThat(result.getNotUpdated()).containsKey(requestMessageId);
        assertThat(result.getNotUpdated().get(requestMessageId).getProperties()).isPresent();
        assertThat(result.getNotUpdated().get(requestMessageId).getProperties().get()).contains(invalidProperty);
        assertThat(result.getUpdated()).isEmpty();
    }

    @Test
    public void assertUserCanSendFromShouldNotThrowWhenSenderIsAnAliasOfTheConnectedUser() throws RecipientRewriteTableException, AddressException {
        Username sender = Username.of("alias@example.com");

        recipientRewriteTable.addAliasMapping(MappingSource.fromUser("alias", "example.com"), USER.asString());

        assertThatCode(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .doesNotThrowAnyException();
    }

    @Test
    public void assertUserCanSendFromShouldThrowWhenSenderIsAnAliasOfAnotherUser() throws RecipientRewriteTableException, AddressException {
        Username sender = Username.of("alias@example.com");

        recipientRewriteTable.addAliasMapping(MappingSource.fromUser("alias", "example.com"), OTHER_USER.asString());

        assertThatThrownBy(() -> sut.assertUserCanSendFrom(USER, Optional.of(sender)).block())
            .hasCause(new MailboxSendingNotAllowedException(USER, Optional.of(sender)));
    }

}