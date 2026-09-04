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

package org.apache.james.mailbox.cassandra.mail.migration;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Backfills the fields `messageIdTable` and `imapUidTable` denormalize from `messagev3`.
 *
 * <p>Those columns were introduced by JAMES-3576 in 3.7.0 and never backfilled: JAMES-3815 chose instead
 * to tolerate their absence, which is what {@link CassandraMessageMetadata#isComplete()} tests, falling
 * back to `messagev3` when they are missing. Messages written by James 3.6 and earlier therefore still
 * carry null there.</p>
 *
 * <p>Once every row is complete, that fallback becomes dead: metadata and header fetches are answered
 * from a single read, and `messagev3` no longer needs to carry the denormalized copies at all.</p>
 *
 * See JAMES-4225
 */
public class MessageDenormalizationMigration implements Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDenormalizationMigration.class);
    private static final int CONCURRENCY = 8;

    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageDAOV3 messageDAO;

    @Inject
    public MessageDenormalizationMigration(CassandraMessageIdDAO messageIdDAO,
                                           CassandraMessageIdToImapUidDAO imapUidDAO,
                                           CassandraMessageDAOV3 messageDAO) {
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.messageDAO = messageDAO;
    }

    @Override
    public void apply() {
        backfill()
            .then(cleanUpPartialRows())
            .block();
    }

    private Mono<Void> backfill() {
        return Flux.concat(
                backfill(imapUidDAO.retrieveAllMessages(), this::backfillImapUid),
                backfill(messageIdDAO.retrieveAllMessages(), this::backfillMessageId))
            .then();
    }

    private Mono<Void> cleanUpPartialRows() {
        return Flux.concat(imapUidDAO.retrieveAllMessages(), messageIdDAO.retrieveAllMessages())
            .then();
    }

    private Flux<Void> backfill(Flux<CassandraMessageMetadata> rows,
                                Function<CassandraMessageMetadata, Mono<Void>> backfill) {
        return rows.filter(metadata -> !metadata.isComplete())
            .flatMap(backfill, CONCURRENCY);
    }

    private Mono<Void> backfillMessageId(CassandraMessageMetadata metadata) {
        ComposedMessageId id = metadata.getComposedMessageId().getComposedMessageId();

        return representation(metadata)
            .flatMap(representation -> messageIdDAO.updateDenormalizedFields(
                (CassandraId) id.getMailboxId(),
                id.getUid(),
                representation.getInternalDate(),
                representation.getBodyStartOctet(),
                representation.getSize(),
                representation.getHeaderId()));
    }

    private Mono<Void> backfillImapUid(CassandraMessageMetadata metadata) {
        ComposedMessageId id = metadata.getComposedMessageId().getComposedMessageId();

        return representation(metadata)
            .flatMap(representation -> imapUidDAO.updateDenormalizedFields(
                (CassandraMessageId) id.getMessageId(),
                (CassandraId) id.getMailboxId(),
                id.getUid(),
                representation.getInternalDate(),
                representation.getBodyStartOctet(),
                representation.getSize(),
                representation.getHeaderId()));
    }

    private Mono<MessageRepresentation> representation(CassandraMessageMetadata metadata) {
        return messageDAO.retrieveMessage(metadata.getComposedMessageId(), FetchType.METADATA)
            .doOnError(e -> LOGGER.error("Failed to read messagev3 for {}",
                metadata.getComposedMessageId().getComposedMessageId(), e))
            .onErrorResume(e -> Mono.empty());
    }
}
