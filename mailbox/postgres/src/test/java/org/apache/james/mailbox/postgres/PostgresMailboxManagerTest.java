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
package org.apache.james.mailbox.postgres;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Optional;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresMailboxManagerTest extends MailboxManagerTest<PostgresMailboxManager> {

    @Disabled("JPAMailboxManager is using DefaultMessageId which doesn't support full feature of a messageId, which is an essential" +
        " element of the Vault")
    @Nested
    class HookTests {
    }

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    Optional<PostgresMailboxManager> mailboxManager = Optional.empty();

    @Override
    protected PostgresMailboxManager provideMailboxManager() {
        if (mailboxManager.isEmpty()) {
            mailboxManager = Optional.of(PostgresMailboxManagerProvider.provideMailboxManager(postgresExtension));
        }
        return mailboxManager.get();
    }

    @Override
    protected SubscriptionManager provideSubscriptionManager() {
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory(), provideMailboxManager().getMapperFactory(), provideMailboxManager().getEventBus());
    }

    @Override
    protected EventBus retrieveEventBus(PostgresMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }

    @Nested
    class DeletionTests {
        private MailboxSession session;
        private MailboxPath inbox;
        private MailboxId inboxId;
        private MessageManager inboxManager;
        private MessageManager otherBoxManager;
        private MailboxPath newPath;
        private PostgresMailboxManager mailboxManager;
        private PostgresMessageDAO postgresMessageDAO;
        private PostgresMailboxMessageDAO postgresMailboxMessageDAO;

        @BeforeEach
        void setUp() throws Exception {
            mailboxManager = provideMailboxManager();
            session = mailboxManager.createSystemSession(USER_1);
            inbox = MailboxPath.inbox(session);
            newPath = MailboxPath.forUser(USER_1, "specialMailbox");

            inboxId = mailboxManager.createMailbox(inbox, session).get();
            inboxManager = mailboxManager.getMailbox(inbox, session);
            MailboxId otherId = mailboxManager.createMailbox(newPath, session).get();
            otherBoxManager = mailboxManager.getMailbox(otherId, session);

            postgresMessageDAO = spy(PostgresMailboxManagerProvider.providePostgresMessageDAO());
            postgresMailboxMessageDAO = spy(PostgresMailboxManagerProvider.providePostgresMailboxMessageDAO());
        }

        @Test
        void deleteMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();
                PostgresMailboxId mailboxId = (PostgresMailboxId) appendResult.getId().getMailboxId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isEmpty();

                softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId(mailboxId).block())
                    .isEqualTo(0);
            });
        }

        @Test
        void deleteMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
            mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isNotEmpty();
            });
        }

        @Test
        void deleteMessageInMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isEmpty();
            });
        }

        @Test
        void deleteMessageInMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
            mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isNotEmpty();
            });
        }

        @Test
        void deleteMailboxShouldEventuallyDeleteUnreferencedMessageMetadataWhenDeletingMailboxMessageFail() throws Exception {
            doReturn(Flux.error(new RuntimeException("Fake exception")))
                .doCallRealMethod()
                .when(postgresMailboxMessageDAO).deleteByMailboxId(Mockito.any());

            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            mailboxManager.deleteMailbox(inbox, session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();
                PostgresMailboxId mailboxId = (PostgresMailboxId) appendResult.getId().getMailboxId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isEmpty();

                softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId(mailboxId).block())
                    .isEqualTo(0);
            });
        }

        @Test
        void deleteMessageInMailboxShouldEventuallyDeleteUnreferencedMessageMetadataWhenDeletingMessageFail() throws Exception {
            doReturn(Mono.error(new RuntimeException("Fake exception")))
                .doCallRealMethod()
                .when(postgresMessageDAO).deleteByMessageId(Mockito.any());

            MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

            inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

            SoftAssertions.assertSoftly(softly -> {
                PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

                softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                    .isEmpty();
            });
        }
    }
}
