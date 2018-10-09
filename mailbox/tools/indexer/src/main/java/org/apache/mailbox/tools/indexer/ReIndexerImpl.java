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

package org.apache.mailbox.tools.indexer;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.util.streams.Iterators;
import org.apache.mailbox.tools.indexer.events.ImpactingEventType;
import org.apache.mailbox.tools.indexer.events.ImpactingMessageEvent;
import org.apache.mailbox.tools.indexer.registrations.GlobalRegistration;
import org.apache.mailbox.tools.indexer.registrations.MailboxRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Lists;

/**
 * Note about live re-indexation handling :
 *
 *  - Data races may arise... If you modify the stored value between the received event check and the index operation,
 *  you have an inconsistent behavior.
 *
 *  This class is more about supporting changes in real time for future indexed values. If you change a flags / delete
 *  mails for instance, you will see it in the indexed value !
 *
 *  Why only care about updates and deletions ? Additions are already handled by the indexer that behaves normaly. We
 *  should just "adapt" our indexed value to the latest value, if any. The normal indexer will take care of new stuff.
 */
public class ReIndexerImpl implements ReIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReIndexerImpl.class);

    private static final int NO_LIMIT = 0;
    private static final int SINGLE_MESSAGE = 1;

    private final MailboxManager mailboxManager;
    private final ListeningMessageSearchIndex messageSearchIndex;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Inject
    public ReIndexerImpl(MailboxManager mailboxManager,
                         ListeningMessageSearchIndex messageSearchIndex,
                         MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        this.mailboxManager = mailboxManager;
        this.messageSearchIndex = messageSearchIndex;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    @Override
    public void reIndex(MailboxPath path) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(path.getUser());
        reIndex(path, mailboxSession);
    }


    @Override
    public void reIndex() throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession("re-indexing");
        LOGGER.info("Starting a full reindex");
        List<MailboxPath> mailboxPaths = mailboxManager.list(mailboxSession);
        GlobalRegistration globalRegistration = new GlobalRegistration();
        mailboxManager.addGlobalListener(globalRegistration, mailboxSession);
        try {
            handleFullReindexingIterations(mailboxPaths, globalRegistration);
        } finally {
            mailboxManager.removeGlobalListener(globalRegistration, mailboxSession);
        }
        LOGGER.info("Full reindex finished");
    }

    private void handleFullReindexingIterations(List<MailboxPath> mailboxPaths, GlobalRegistration globalRegistration) {
        for (MailboxPath mailboxPath : mailboxPaths) {
            Optional<MailboxPath> pathToIndex = globalRegistration.getPathToIndex(mailboxPath);
            if (pathToIndex.isPresent()) {
                try {
                    reIndex(pathToIndex.get());
                } catch (Throwable e) {
                    LOGGER.error("Error while proceeding to full reindexing on {}", pathToIndex.get(), e);
                }
            }
        }
    }


    private void reIndex(MailboxPath path, MailboxSession mailboxSession) throws MailboxException {
        MailboxRegistration mailboxRegistration = new MailboxRegistration(path);
        LOGGER.info("Intend to reindex {}",path);
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(path);
        messageSearchIndex.deleteAll(mailboxSession, mailbox);
        mailboxManager.addListener(path, mailboxRegistration, mailboxSession);
        try {
            Iterators.toStream(
                mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
                    .findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, NO_LIMIT))
                .map(MailboxMessage::getUid)
                .forEach(uid -> handleMessageReIndexing(mailboxSession, mailboxRegistration, mailbox, uid));
            LOGGER.info("Finish to reindex {}", path);
        } finally {
            mailboxManager.removeListener(path, mailboxRegistration, mailboxSession);
        }
    }

    private void handleMessageReIndexing(MailboxSession mailboxSession, MailboxRegistration mailboxRegistration, Mailbox mailbox, MessageUid uid) {
        try {
            Optional<ImpactingMessageEvent> impactingMessageEvent = findMostRelevant(mailboxRegistration.getImpactingEvents(uid));

            Optional.of(uid)
                .filter(x -> !wasDeleted(impactingMessageEvent))
                .flatMap(Throwing.function(mUid -> fullyReadMessage(mailboxSession, mailbox, mUid)))
                .map(message -> messageUpdateRegardingEvents(message, impactingMessageEvent))
                .ifPresent(Throwing.consumer(message -> messageSearchIndex.add(mailboxSession, mailbox, message)));
        } catch (Exception e) {
            LOGGER.warn("ReIndexing failed for {} {}", mailbox.generateAssociatedPath(), uid, e);
        }
    }

    private Optional<ImpactingMessageEvent> findMostRelevant(List<ImpactingMessageEvent> messageEvents) {
        for (ImpactingMessageEvent impactingMessageEvent : messageEvents) {
            if (impactingMessageEvent.getType().equals(ImpactingEventType.Deletion)) {
                return Optional.of(impactingMessageEvent);
            }
        }
        return Lists.reverse(messageEvents).stream().findFirst();
    }

    private boolean wasDeleted(Optional<ImpactingMessageEvent> impactingMessageEvent) {
        return impactingMessageEvent.map(ImpactingMessageEvent::wasDeleted).orElse(false);
    }

    private MailboxMessage messageUpdateRegardingEvents(MailboxMessage message, Optional<ImpactingMessageEvent> impactingMessageEvent) {
        impactingMessageEvent.flatMap(ImpactingMessageEvent::newFlags).ifPresent(message::setFlags);
        return message;
    }

    private Optional<MailboxMessage> fullyReadMessage(MailboxSession mailboxSession, Mailbox mailbox, MessageUid mUid) throws MailboxException {
        return Iterators.toStream(mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
            .findInMailbox(mailbox, MessageRange.one(mUid), MessageMapper.FetchType.Full, SINGLE_MESSAGE))
            .findFirst();
    }

}
