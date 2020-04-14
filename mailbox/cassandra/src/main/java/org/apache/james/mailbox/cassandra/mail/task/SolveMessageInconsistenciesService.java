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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SolveMessageInconsistenciesService {
    public static final Logger LOGGER = LoggerFactory.getLogger(SolveMessageInconsistenciesService.class);

    private final CassandraMessageIdToImapUidDAO idToImapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;

    @Inject
    SolveMessageInconsistenciesService(CassandraMessageIdToImapUidDAO idToImapUidDAO, CassandraMessageIdDAO messageIdDAO) {
        this.idToImapUidDAO = idToImapUidDAO;
        this.messageIdDAO = messageIdDAO;
    }

    Mono<Task.Result> fixMessageInconsistencies() {
        return Flux.concat(
            fixMessageIdInconsistencies(),
            fixImapUidInconsistencies())
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Mono<Task.Result> fixMessageIdInconsistencies() {
        return idToImapUidDAO.retrieveAllMessages()
            .concatMap(this::fetchAndFixMessageId)
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Mono<Task.Result> fetchAndFixMessageId(ComposedMessageIdWithMetaData message) {
        return idToImapUidDAO.retrieve((CassandraMessageId) message.getComposedMessageId().getMessageId(), Optional.of((CassandraId) message.getComposedMessageId().getMailboxId()))
            .single()
            .flatMap(upToDateMessage -> messageIdDAO.retrieve((CassandraId) upToDateMessage.getComposedMessageId().getMailboxId(), upToDateMessage.getComposedMessageId().getUid())
                .flatMap(Mono::justOrEmpty)
                .flatMap(fetchedFromMessageId -> fixWhenMessageFoundInMessageId(upToDateMessage, fetchedFromMessageId)))
            .switchIfEmpty(fixWhenMessageNotFoundInMessageId(message));
    }

    private Mono<Task.Result> fixWhenMessageFoundInMessageId(ComposedMessageIdWithMetaData messageFromImapUid, ComposedMessageIdWithMetaData messageFromMessageId) {
        return Mono.fromCallable(() -> messageFromImapUid.equals(messageFromMessageId))
            .flatMap(isEqual -> {
                if (isEqual) {
                    return Mono.just(Task.Result.COMPLETED);
                }

                return messageIdDAO.updateMetadata(messageFromImapUid)
                    .then(Mono.just(Task.Result.COMPLETED))
                    .onErrorResume(error -> {
                        LOGGER.error("Error when fixing inconsistency for message: {}", messageFromImapUid, error);
                        return Mono.just(Task.Result.PARTIAL);
                    });
            });
    }

    private Mono<Task.Result> fixWhenMessageNotFoundInMessageId(ComposedMessageIdWithMetaData message) {
        return messageIdDAO.insert(message)
            .then(Mono.just(Task.Result.COMPLETED))
            .onErrorResume(error -> {
                LOGGER.error("Error when fixing inconsistency for message: {}", message, error);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    @VisibleForTesting
    Mono<Task.Result> fixImapUidInconsistencies() {
        return messageIdDAO.retrieveAllMessages()
            .concatMap(message -> process(message))
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    private Mono<Task.Result> process(ComposedMessageIdWithMetaData message) {
        return messageIdDAO.retrieve((CassandraId) message.getComposedMessageId().getMailboxId(), message.getComposedMessageId().getUid())
            .flatMap(Mono::justOrEmpty)
            .flatMap(this::fixWhenMessageFound)
            .switchIfEmpty(Mono.just(Task.Result.COMPLETED));
    }

    private Mono<Task.Result> fixWhenMessageFound(ComposedMessageIdWithMetaData message) {
        return idToImapUidDAO.retrieve((CassandraMessageId) message.getComposedMessageId().getMessageId(), Optional.of((CassandraId) message.getComposedMessageId().getMailboxId()))
            .flatMap(uidRecord -> {
                if (uidRecord.equals(message)) {
                    return Mono.just(Task.Result.COMPLETED);
                }

                return messageIdDAO.updateMetadata(uidRecord)
                    .then(Mono.just(Task.Result.COMPLETED));
            })
            .switchIfEmpty(messageIdDAO.delete((CassandraId) message.getComposedMessageId().getMailboxId(), message.getComposedMessageId().getUid())
                .then(Mono.just(Task.Result.COMPLETED)))
            .single()
            .onErrorResume(error -> {
                LOGGER.error("Error when fixing inconsistency for message {}", message, error);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
