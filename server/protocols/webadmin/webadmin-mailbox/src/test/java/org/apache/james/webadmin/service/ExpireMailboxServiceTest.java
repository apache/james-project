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

package org.apache.james.webadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.TimeZone;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

public class ExpireMailboxServiceTest {

    private static class FailingSearchIndex implements MessageSearchIndex {

        private MessageSearchIndex delegate;

        private int failuresRemaining = 0;

        public MessageSearchIndex setDelegate(MessageSearchIndex delegate) {
            this.delegate = delegate;
            return this;
        }

        public void generateFailures(int count) {
            this.failuresRemaining = count;
        }

        private synchronized void handleFailure() throws MailboxException {
            if (failuresRemaining > 0) {
                --failuresRemaining;
                throw new MailboxException("search failed");
            }
        }

        @Override
        public Flux<MessageUid> search(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
            handleFailure();
            return delegate.search(session, mailbox, searchQuery);
        }

        @Override
        public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) throws MailboxException {
            handleFailure();
            return delegate.search(session, mailboxIds, searchQuery, limit);
        }

        @Override
        public EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
            return delegate.getSupportedCapabilities(messageCapabilities);
        }
    }
    
    private static final ExpireMailboxService.RunningOptions OLDER_THAN_1S = new ExpireMailboxService.RunningOptions(1, MailboxConstants.INBOX, false, Optional.of("1s"));

    private final Username alice = Username.of("alice@example.org");
    private final MailboxPath aliceInbox = MailboxPath.inbox(alice);

    private UsersRepository usersRepository;

    private MailboxSession aliceSession;
    private MailboxManager mailboxManager;
    private FailingSearchIndex searchIndex;
    
    private ExpireMailboxService testee;
    
    @BeforeEach
    public void setUp() {
        searchIndex = new FailingSearchIndex();
        
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .searchIndex(stage -> searchIndex.setDelegate(new SimpleMessageSearchIndex(stage.getMapperFactory(), stage.getMapperFactory(), new DefaultTextExtractor(), stage.getAttachmentContentLoader())))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        usersRepository = mock(UsersRepository.class);

        mailboxManager = resources.getMailboxManager();
        aliceSession = mailboxManager.createSystemSession(alice);
        
        testee = new ExpireMailboxService(usersRepository, mailboxManager);
    }
    
    private static Date asDate(ZonedDateTime dateTime) {
        return Date.from(dateTime.toInstant());
    }
 
    private ComposedMessageId appendMessage(MessageManager messageManager, MailboxSession session, ZonedDateTime internalDate) throws Exception {
        return appendMessage(messageManager, session, Message.Builder.of()
            .setSubject("test")
            .setBody("whatever", StandardCharsets.UTF_8)
            .setDate(asDate(internalDate))
            .build());
    }

    private ComposedMessageId appendMessage(MessageManager messageManager, MailboxSession session, Message mailContent) throws Exception {
        return messageManager.appendMessage(MessageManager.AppendCommand.builder()
                .withInternalDate(mailContent.getDate())
                .build(mailContent), session)
            .getId();
    }


    @Test
    void testIgnoresUserListFailure() {
        when(usersRepository.listReactive()).thenReturn(Flux.error(new UsersRepositoryException("it is broken")));

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.getInboxesExpired()).isEqualTo(0);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(0);
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
    }

    @Test
    void testIgnoresMissingMailbox() {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));
        
        // intentionally no mailbox creation
        
        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(0);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(0); // skipped
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
    }
    
    @Test
    void testIgnoresEmptyMailbox() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));
       
        mailboxManager.createMailbox(aliceInbox, aliceSession);

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();
        
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(0);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(1);
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
    }

    @Test
    void testHandlesMailboxErrors() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));

        mailboxManager.createMailbox(aliceInbox, aliceSession);
        MessageManager aliceManager = mailboxManager.getMailbox(aliceInbox, aliceSession);
        appendMessage(aliceManager, aliceSession, ZonedDateTime.now());

        searchIndex.generateFailures(1);
        
        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.getInboxesExpired()).isEqualTo(0);
        assertThat(context.getInboxesFailed()).isEqualTo(1);
        assertThat(context.getInboxesProcessed()).isEqualTo(1);
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
    }

    @Test
    void testExpiresMailboxByAge() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));

        mailboxManager.createMailbox(aliceInbox, aliceSession);
        MessageManager aliceManager = mailboxManager.getMailbox(aliceInbox, aliceSession);
        appendMessage(aliceManager, aliceSession, ZonedDateTime.now().minusSeconds(5));

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(1);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(1);
        assertThat(context.getMessagesDeleted()).isEqualTo(1);

        assertThat(aliceManager.getMessageCount(aliceSession)).isEqualTo(0);
    }

    @Test
    void testExpiresMailboxByHeader() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));

        mailboxManager.createMailbox(aliceInbox, aliceSession);
        MessageManager aliceManager = mailboxManager.getMailbox(aliceInbox, aliceSession);

        ZonedDateTime created = ZonedDateTime.now();
        ZonedDateTime expires = created.plusSeconds(5); 
        ZonedDateTime now = expires.plusSeconds(10);

        appendMessage(aliceManager, aliceSession, Message.Builder.of()
            .setSubject("test")
            .setBody("whatever", StandardCharsets.UTF_8)
            .setDate(asDate(created))
            .setField(Fields.date("Expires", asDate(expires), TimeZone.getTimeZone(expires.getZone())))
            .build()
        );

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        ExpireMailboxService.RunningOptions options = new ExpireMailboxService.RunningOptions(1, MailboxConstants.INBOX, true, Optional.empty());
        Task.Result result = testee.expireMailboxes(context, options, asDate(now)).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(1);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(1);
        assertThat(context.getMessagesDeleted()).isEqualTo(1);

        assertThat(aliceManager.getMessageCount(aliceSession)).isEqualTo(0);
    }

    @Test
    void testExpiresNamedMailbox() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));

        String mailboxName = "Archived";
        MailboxPath mailboxPath = MailboxPath.forUser(alice, mailboxName);
        mailboxManager.createMailbox(mailboxPath, aliceSession);
        MessageManager aliceManager = mailboxManager.getMailbox(mailboxPath, aliceSession);
        appendMessage(aliceManager, aliceSession, ZonedDateTime.now().minusSeconds(5));

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        ExpireMailboxService.RunningOptions options = new ExpireMailboxService.RunningOptions(1, mailboxName, false, Optional.of("1s"));
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, options, now).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(1);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(1);
        assertThat(context.getMessagesDeleted()).isEqualTo(1);

        assertThat(aliceManager.getMessageCount(aliceSession)).isEqualTo(0);
    }

    @Test
    void testExpiresNamedMailbox2() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(alice));

        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        ExpireMailboxService.RunningOptions options = new ExpireMailboxService.RunningOptions(1, "NoSuchMailbox", false, Optional.of("1s"));
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, options, now).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.getInboxesExpired()).isEqualTo(0);
        assertThat(context.getInboxesFailed()).isEqualTo(0);
        assertThat(context.getInboxesProcessed()).isEqualTo(0);
        assertThat(context.getMessagesDeleted()).isEqualTo(0);
    }

    @Test
    void testContinuesAfterFailure() throws Exception {
        Username bob = Username.of("bob@example.org");
        MailboxPath bobInbox = MailboxPath.inbox(bob);
        MailboxSession bobSession = mailboxManager.createSystemSession(bob);

        when(usersRepository.listReactive()).thenReturn(Flux.just(alice, bob));

        mailboxManager.createMailbox(aliceInbox, aliceSession);
        MessageManager aliceManager = mailboxManager.getMailbox(aliceInbox, aliceSession);
        appendMessage(aliceManager, aliceSession, ZonedDateTime.now().minusSeconds(5));

        mailboxManager.createMailbox(bobInbox, bobSession);
        MessageManager bobManager = mailboxManager.getMailbox(bobInbox, bobSession);
        appendMessage(bobManager, bobSession, ZonedDateTime.now().minusSeconds(5));

        searchIndex.generateFailures(1);
        
        ExpireMailboxService.Context context = new ExpireMailboxService.Context();
        Date now = new Date();
        Task.Result result = testee.expireMailboxes(context, OLDER_THAN_1S, now).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.getInboxesExpired()).isEqualTo(1);
        assertThat(context.getInboxesFailed()).isEqualTo(1);
        assertThat(context.getInboxesProcessed()).isEqualTo(2);
        assertThat(context.getMessagesDeleted()).isEqualTo(1);

        assertThat(aliceManager.getMessageCount(aliceSession)).isEqualTo(1);
        assertThat(bobManager.getMessageCount(bobSession)).isEqualTo(0);
    }
}
