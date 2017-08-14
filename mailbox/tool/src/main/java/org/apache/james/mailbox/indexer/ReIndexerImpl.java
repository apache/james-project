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

package org.apache.james.mailbox.indexer;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.events.FlagsMessageEvent;
import org.apache.james.mailbox.indexer.events.ImpactingEventType;
import org.apache.james.mailbox.indexer.events.ImpactingMessageEvent;
import org.apache.james.mailbox.indexer.registrations.GlobalRegistration;
import org.apache.james.mailbox.indexer.registrations.MailboxRegistration;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;

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
    public static final int NO_LIMIT = 0;

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

    public void reIndex(MailboxPath path) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(path.getUser());
        reIndex(path, mailboxSession);
    }


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

    private void reIndex(MailboxPath path, MailboxSession mailboxSession) throws MailboxException {
        MailboxRegistration mailboxRegistration = new MailboxRegistration(path);
        LOGGER.info("Intend to reindex {}",path);
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(path);
        messageSearchIndex.deleteAll(mailboxSession, mailbox);
        mailboxManager.addListener(path, mailboxRegistration, mailboxSession);
        try {
            handleMailboxIndexingIterations(mailboxSession,
                mailboxRegistration,
                mailbox,
                mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
                    .findInMailbox(mailbox,
                        MessageRange.all(),
                        MessageMapper.FetchType.Full,
                        NO_LIMIT));
            LOGGER.info("Finish to reindex " + path);
        } finally {
            mailboxManager.removeListener(path, mailboxRegistration, mailboxSession);
        }
    }

    private void handleFullReindexingIterations(List<MailboxPath> mailboxPaths, GlobalRegistration globalRegistration) throws MailboxException {
        for (MailboxPath mailboxPath : mailboxPaths) {
            Optional<MailboxPath> pathToIndex = globalRegistration.getPathToIndex(mailboxPath);
            if (pathToIndex.isPresent()) {
                try {
                    reIndex(pathToIndex.get());
                } catch(Throwable e) {
                    LOGGER.error("Error while proceeding to full reindexing on {}", pathToIndex.get(), e);
                }
            }
        }
    }

    private void handleMailboxIndexingIterations(MailboxSession mailboxSession, MailboxRegistration mailboxRegistration, Mailbox mailbox, Iterator<MailboxMessage> iterator) throws MailboxException {
        while (iterator.hasNext()) {
            MailboxMessage message = iterator.next();
            ImpactingMessageEvent impactingMessageEvent = findMostRelevant(mailboxRegistration.getImpactingEvents(message.getUid()));
            if (impactingMessageEvent == null) {
                messageSearchIndex.add(mailboxSession, mailbox, message);
            } else if (impactingMessageEvent instanceof FlagsMessageEvent) {
                message.setFlags(((FlagsMessageEvent) impactingMessageEvent).getFlags());
                messageSearchIndex.add(mailboxSession, mailbox, message);
            }
        }
    }

    private ImpactingMessageEvent findMostRelevant(Collection<ImpactingMessageEvent> messageEvents) {
        for (ImpactingMessageEvent impactingMessageEvent : messageEvents) {
            if (impactingMessageEvent.getType().equals(ImpactingEventType.Deletion)) {
                return impactingMessageEvent;
            }
        }
        return Iterables.getLast(messageEvents, null);
    }

}
