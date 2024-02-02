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

package org.apache.james.mailbox.cassandra.mail.task;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.ACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.task.Task;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Limit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MailboxMergingTaskRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxMergingTaskRunner.class);

    private final StoreMessageIdManager messageIdManager;
    private final CassandraMessageIdDAO cassandraMessageIdDAO;
    private final CassandraMailboxDAO mailboxDAO;
    private final ACLMapper aclMapper;
    private final MailboxSession mailboxSession;

    @Inject
    public MailboxMergingTaskRunner(MailboxManager mailboxManager, StoreMessageIdManager messageIdManager, CassandraMessageIdDAO cassandraMessageIdDAO, CassandraMailboxDAO mailboxDAO, ACLMapper aclMapper) {
        this.mailboxSession = mailboxManager.createSystemSession(Username.of("task"));
        this.messageIdManager = messageIdManager;
        this.cassandraMessageIdDAO = cassandraMessageIdDAO;
        this.mailboxDAO = mailboxDAO;
        this.aclMapper = aclMapper;
    }

    public Task.Result run(CassandraId oldMailboxId, CassandraId newMailboxId, MailboxMergingTask.Context context) {
        return moveMessages(oldMailboxId, newMailboxId, mailboxSession, context)
            .onComplete(
                () -> mergeRights(oldMailboxId, newMailboxId).block(),
                () -> mailboxDAO.delete(oldMailboxId).block());
    }

    private Task.Result moveMessages(CassandraId oldMailboxId, CassandraId newMailboxId, MailboxSession session, MailboxMergingTask.Context context) {
        return cassandraMessageIdDAO.retrieveMessages(oldMailboxId, MessageRange.all(), Limit.unlimited())
            .map(CassandraMessageMetadata::getComposedMessageId)
            .map(ComposedMessageIdWithMetaData::getComposedMessageId)
            .concatMap(messageId -> Mono.fromCallable(() -> moveMessage(newMailboxId, messageId, session, context)).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .block();
    }

    private Task.Result moveMessage(CassandraId newMailboxId, ComposedMessageId composedMessageId, MailboxSession session, MailboxMergingTask.Context context) {
        try {
            messageIdManager.setInMailboxesNoCheck(composedMessageId.getMessageId(), newMailboxId, session);
            context.incrementMovedCount();
            return Task.Result.COMPLETED;
        } catch (OverQuotaException e) {
            LOGGER.warn("Failed moving message {} due to quota error", composedMessageId.getMessageId(), e);
            context.incrementFailedCount();
            return Task.Result.PARTIAL;
        } catch (MailboxException e) {
            LOGGER.warn("Failed moving message {}", composedMessageId.getMessageId(), e);
            context.incrementFailedCount();
            return Task.Result.PARTIAL;
        }
    }

    private Mono<Void> mergeRights(CassandraId oldMailboxId, CassandraId newMailboxId) {
            return Flux.concat(
                    aclMapper.getACL(oldMailboxId),
                    aclMapper.getACL(newMailboxId))
                .reduce(Throwing.biFunction(MailboxACL::union))
                .flatMap(union -> aclMapper.setACL(newMailboxId, union))
                .then();
    }
}
