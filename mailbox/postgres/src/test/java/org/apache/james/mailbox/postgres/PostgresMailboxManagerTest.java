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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresMailboxManagerTest extends MailboxManagerTest<PostgresMailboxManager> {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    Optional<PostgresMailboxManager> mailboxManager = Optional.empty();

    @Override
    protected PostgresMailboxManager provideMailboxManager() {
        if (mailboxManager.isEmpty()) {
            mailboxManager = Optional.of(PostgresMailboxManagerProvider.provideMailboxManager(postgresExtension,
                new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory())));
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

    @Test
    void expungeMessageShouldCorrectWhenALotOfMessages() throws Exception {
        // Given a mailbox with 6000 messages
        Username username = Username.of("tung");
        PostgresMailboxManager postgresMailboxManager = mailboxManager.get();
        MailboxSession session = postgresMailboxManager.createSystemSession(username);
        postgresMailboxManager.createMailbox(MailboxPath.inbox(username), session).get();
        MessageManager inboxManager = postgresMailboxManager.getMailbox(MailboxPath.inbox(session), session);

        int totalMessages = 6000;
        Flux.range(0, totalMessages)
            .flatMap(i -> Mono.fromCallable(() -> inboxManager.appendMessage(MessageManager.AppendCommand.builder().build(Message.Builder.of()
                .setSubject("test" + i)
                .setBody("testmail" + i, StandardCharsets.UTF_8)), session)), 100)
            .collectList().block();
        // When expunge all messages
        inboxManager.setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD, MessageRange.all(), session);

        List<MessageUid> expungeList = inboxManager.expungeReactive(MessageRange.all(), session)
            .collectList().block();

        // Then all messages are expunged
        assertThat(expungeList).hasSize(totalMessages);
    }
}
