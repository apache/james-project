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

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMailbox;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.MessageFactory;
import org.apache.james.mailbox.store.MessageStorer;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class PostgresMessageManager extends StoreMessageManager {

    private final MailboxSessionMapperFactory mapperFactory;
    private final StoreRightManager storeRightManager;
    private final Mailbox mailbox;

    public PostgresMessageManager(PostgresMailboxSessionMapperFactory mapperFactory,
                                  MessageSearchIndex index, EventBus eventBus,
                                  MailboxPathLocker locker, Mailbox mailbox,
                                  QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
                                  MessageParser messageParser,
                                  MessageId.Factory messageIdFactory, BatchSizes batchSizes,
                                  StoreRightManager storeRightManager, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm,
                                  Clock clock, PreDeletionHooks preDeletionHooks) {
        super(StoreMailboxManager.DEFAULT_NO_MESSAGE_CAPABILITIES, mapperFactory, index, eventBus, locker, mailbox,
            quotaManager, quotaRootResolver, batchSizes, storeRightManager, preDeletionHooks,
            new MessageStorer.WithAttachment(mapperFactory, messageIdFactory, new MessageFactory.StoreMessageFactory(), mapperFactory, messageParser, threadIdGuessingAlgorithm, clock));
        this.storeRightManager = storeRightManager;
        this.mapperFactory = mapperFactory;
        this.mailbox = mailbox;
    }


    @Override
    public Flags getPermanentFlags(MailboxSession session) {
        Flags flags = super.getPermanentFlags(session);
        flags.add(Flags.Flag.USER);
        return flags;
    }

    public Mono<MailboxMetaData> getMetaDataReactive(MailboxMetaData.RecentMode recentMode, MailboxSession mailboxSession, EnumSet<MailboxMetaData.Item> items) throws MailboxException {
        if (!storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return Mono.just(MailboxMetaData.sensibleInformationFree(getResolvedAcl(mailboxSession), getMailboxEntity().getUidValidity(), isWriteable(mailboxSession)));
        }

        Flags permanentFlags = getPermanentFlags(mailboxSession);
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        Mono<PostgresMailbox> postgresMailboxMetaDataPublisher = Mono.just(mapperFactory.getMailboxMapper(mailboxSession))
            .flatMap(postgresMailboxMapper -> postgresMailboxMapper.findMailboxById(getMailboxEntity().getMailboxId())
                .map(mailbox -> (PostgresMailbox) mailbox));

        Mono<Tuple2<Optional<MessageUid>, List<MessageUid>>> firstUnseenAndRecentPublisher = Mono.zip(firstUnseen(messageMapper, items), recent(recentMode, mailboxSession));

        return messageMapper.executeReactive(Mono.zip(postgresMailboxMetaDataPublisher, mailboxCounters(messageMapper, items))
            .flatMap(metadataAndCounter -> {
                PostgresMailbox metadata = metadataAndCounter.getT1();
                MailboxCounters counters = metadataAndCounter.getT2();
                return firstUnseenAndRecentPublisher.map(firstUnseenAndRecent -> new MailboxMetaData(
                    firstUnseenAndRecent.getT2(),
                    permanentFlags,
                    metadata.getUidValidity(),
                    nextUid(metadata),
                    metadata.getHighestModSeq(),
                    counters.getCount(),
                    counters.getUnseen(),
                    firstUnseenAndRecent.getT1().orElse(null),
                    isWriteable(mailboxSession),
                    metadata.getACL()));
            }));
    }

    private MessageUid nextUid(PostgresMailbox mailboxMetaData) {
        return Optional.ofNullable(mailboxMetaData.getLastUid())
            .map(MessageUid::next)
            .orElse(MessageUid.MIN_VALUE);
    }
}
