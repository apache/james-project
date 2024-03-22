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

package org.apache.james.mailbox.cassandra;

import java.time.Clock;
import java.util.EnumSet;

import jakarta.inject.Inject;

import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import reactor.core.publisher.Mono;

/**
 * Cassandra implementation of {@link StoreMailboxManager}
 */
public class CassandraMailboxManager extends StoreMailboxManager {
    public static final EnumSet<MailboxCapabilities> MAILBOX_CAPABILITIES = EnumSet.of(
        MailboxCapabilities.Move,
        MailboxCapabilities.UserFlag,
        MailboxCapabilities.Namespace,
        MailboxCapabilities.Annotation,
        MailboxCapabilities.ACL,
        MailboxCapabilities.Quota);
    public static final EnumSet<MessageCapabilities> MESSAGE_CAPABILITIES = EnumSet.of(MessageCapabilities.UniqueID);

    private final MailboxPathLocker locker;
    private final CassandraMailboxSessionMapperFactory mapperFactory;

    @Inject
    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, SessionProvider sessionProvider,
                                   MailboxPathLocker locker, MessageParser messageParser,
                                   MessageId.Factory messageIdFactory, EventBus eventBus,
                                   StoreMailboxAnnotationManager annotationManager, StoreRightManager storeRightManager,
                                   QuotaComponents quotaComponents, MessageSearchIndex index,
                                   MailboxManagerConfiguration configuration,
                                   PreDeletionHooks preDeletionHooks, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm, Clock clock) {
        super(mapperFactory,
            sessionProvider,
            locker,
            messageParser,
            messageIdFactory,
            annotationManager,
            eventBus,
            storeRightManager,
            quotaComponents,
            index,
            configuration,
            preDeletionHooks,
            threadIdGuessingAlgorithm,
            clock);
        this.locker = locker;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public EnumSet<MailboxManager.MailboxCapabilities> getSupportedMailboxCapabilities() {
        return MAILBOX_CAPABILITIES;
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return MESSAGE_CAPABILITIES;
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) {
        return new CassandraMessageManager(mapperFactory,
            getMessageSearchIndex(),
            getEventBus(),
            this.locker,
            mailboxRow,
            getQuotaComponents().getQuotaManager(),
            getQuotaComponents().getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            configuration.getBatchSizes(),
            getStoreRightManager(),
            getPreDeletionHooks(),
            getThreadIdGuessingAlgorithm(),
            getClock());
    }

    @Override
    public <T> Mono<T> manageProcessing(Mono<T> toBeWrapped, MailboxSession mailboxSession) {
        return toBeWrapped;
    }
}
