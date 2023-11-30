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

package org.apache.james.mailbox.postgres.mail;

import java.time.Clock;
import java.util.EnumSet;

import javax.mail.Flags;

import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UidValidity;
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
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public class PostgresMessageManager extends StoreMessageManager {

    private final MailboxSessionMapperFactory mapperFactory;
    private final StoreRightManager storeRightManager;
    private final Mailbox mailbox;

    public PostgresMessageManager(MailboxSessionMapperFactory mapperFactory,
                                  MessageSearchIndex index, EventBus eventBus,
                                  MailboxPathLocker locker, Mailbox mailbox,
                                  QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
                                  MessageId.Factory messageIdFactory, BatchSizes batchSizes,
                                  StoreRightManager storeRightManager, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm,
                                  Clock clock) {
        super(StoreMailboxManager.DEFAULT_NO_MESSAGE_CAPABILITIES, mapperFactory, index, eventBus, locker, mailbox,
            quotaManager, quotaRootResolver, batchSizes, storeRightManager, PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new MessageStorer.WithoutAttachment(mapperFactory, messageIdFactory, new MessageFactory.StoreMessageFactory(), threadIdGuessingAlgorithm, clock));
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
        MailboxACL resolvedAcl = getResolvedAcl(mailboxSession);
        if (!storeRightManager.hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)) {
            return Mono.just(MailboxMetaData.sensibleInformationFree(resolvedAcl, getMailboxEntity().getUidValidity(), isWriteable(mailboxSession)));
        }
        Flags permanentFlags = getPermanentFlags(mailboxSession);
        UidValidity uidValidity = getMailboxEntity().getUidValidity();
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);

        return messageMapper.executeReactive(
            nextUid(messageMapper, items)
                .flatMap(nextUid -> highestModSeq(messageMapper, items)
                    .flatMap(highestModSeq -> firstUnseen(messageMapper, items)
                        .flatMap(Throwing.function(firstUnseen -> recent(recentMode, mailboxSession)
                            .flatMap(recents -> mailboxCounters(messageMapper, items)
                                .map(counters -> new MailboxMetaData(recents, permanentFlags, uidValidity, nextUid, highestModSeq, counters.getCount(),
                                    counters.getUnseen(), firstUnseen.orElse(null), isWriteable(mailboxSession), resolvedAcl))))))));
    }
}
