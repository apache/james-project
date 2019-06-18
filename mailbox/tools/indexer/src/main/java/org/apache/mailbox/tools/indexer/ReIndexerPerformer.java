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

import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Task;
import org.apache.james.util.streams.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class ReIndexerPerformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReIndexerPerformer.class);

    private static final int NO_LIMIT = 0;
    private static final int SINGLE_MESSAGE = 1;
    private static final String RE_INDEXING = "re-indexing";

    private final MailboxManager mailboxManager;
    private final ListeningMessageSearchIndex messageSearchIndex;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;

    @Inject
    public ReIndexerPerformer(MailboxManager mailboxManager,
                              ListeningMessageSearchIndex messageSearchIndex,
                              MailboxSessionMapperFactory mailboxSessionMapperFactory) {
        this.mailboxManager = mailboxManager;
        this.messageSearchIndex = messageSearchIndex;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
    }

    Task.Result reIndex(MailboxId mailboxId, ReprocessingContext reprocessingContext) throws Exception {
        LOGGER.info("Intend to reindex mailbox with mailboxId {}", mailboxId.serialize());
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXING);
        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
        messageSearchIndex.deleteAll(mailboxSession, mailbox);
        try {
            return Iterators.toStream(
                mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
                    .findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Metadata, NO_LIMIT))
                .map(MailboxMessage::getUid)
                .map(uid -> handleMessageReIndexing(mailboxSession, mailbox, uid, reprocessingContext))
                .reduce(Task::combine)
                .orElse(Task.Result.COMPLETED);
        } finally {
            LOGGER.info("Finish to reindex mailbox with mailboxId {}", mailboxId.serialize());
        }
    }

    Task.Result reIndex(ReprocessingContext reprocessingContext, ReIndexingExecutionFailures previousReIndexingFailures) {
        return previousReIndexingFailures.failures()
            .stream()
            .map(previousFailure -> reIndex(reprocessingContext, previousFailure))
            .reduce(Task::combine)
            .orElse(Task.Result.COMPLETED);
    }

    private Task.Result reIndex(ReprocessingContext reprocessingContext, ReIndexingExecutionFailures.ReIndexingFailure previousReIndexingFailure) {
        MailboxId mailboxId = previousReIndexingFailure.getMailboxId();
        MessageUid uid = previousReIndexingFailure.getUid();
        try {
            return handleMessageReIndexing(mailboxId, uid, reprocessingContext);
        } catch (MailboxException e) {
            LOGGER.warn("ReIndexing failed for {} {}", mailboxId, uid, e);
            reprocessingContext.recordFailureDetailsForMessage(mailboxId, uid);
            return Task.Result.PARTIAL;
        }
    }

    Task.Result reIndex(ReprocessingContext reprocessingContext) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXING);
        LOGGER.info("Starting a full reindex");
        Stream<MailboxId> mailboxIds = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).list()
            .stream()
            .map(Mailbox::getMailboxId);

        try {
            return reIndex(mailboxIds, reprocessingContext);
        } finally {
            LOGGER.info("Full reindex finished");
        }
    }

    Task.Result reIndex(User user, ReprocessingContext reprocessingContext) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user.asString());
        LOGGER.info("Starting a reindex for user {}", user.asString());

        Stream<MailboxId> mailboxIds = mailboxManager.search(MailboxQuery.privateMailboxesBuilder(mailboxSession).build(), mailboxSession)
            .stream()
            .map(MailboxMetaData::getId);

        try {
            return reIndex(mailboxIds, reprocessingContext);
        } finally {
            LOGGER.info("User {} reindex finished", user.asString());
        }
    }

    Task.Result handleMessageReIndexing(MailboxId mailboxId, MessageUid uid, ReprocessingContext reprocessingContext) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(RE_INDEXING);

        Mailbox mailbox = mailboxSessionMapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
        return handleMessageReIndexing(mailboxSession, mailbox, uid, reprocessingContext);
    }

    private Task.Result reIndex(Stream<MailboxId> mailboxIds, ReprocessingContext reprocessingContext) {
        return mailboxIds
            .map(mailboxId -> {
                try {
                    return reIndex(mailboxId, reprocessingContext);
                } catch (Throwable e) {
                    LOGGER.error("Error while proceeding to full reindexing on mailbox with mailboxId {}", mailboxId.serialize(), e);
                    return Task.Result.PARTIAL;
                }
            })
            .reduce(Task::combine)
            .orElse(Task.Result.COMPLETED);
    }

    private Task.Result handleMessageReIndexing(MailboxSession mailboxSession, Mailbox mailbox, MessageUid uid, ReprocessingContext reprocessingContext) {
        try {
            Optional.of(uid)
                .flatMap(Throwing.function(mUid -> fullyReadMessage(mailboxSession, mailbox, mUid)))
                .ifPresent(Throwing.consumer(message -> messageSearchIndex.add(mailboxSession, mailbox, message)));
            reprocessingContext.recordSuccess();
            return Task.Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.warn("ReIndexing failed for {} {}", mailbox.generateAssociatedPath(), uid, e);
            reprocessingContext.recordFailureDetailsForMessage(mailbox.getMailboxId(), uid);
            return Task.Result.PARTIAL;
        }
    }

    private Optional<MailboxMessage> fullyReadMessage(MailboxSession mailboxSession, Mailbox mailbox, MessageUid mUid) throws MailboxException {
        return Iterators.toStream(mailboxSessionMapperFactory.getMessageMapper(mailboxSession)
            .findInMailbox(mailbox, MessageRange.one(mUid), MessageMapper.FetchType.Full, SINGLE_MESSAGE))
            .findFirst();
    }
}
