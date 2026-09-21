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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;

import java.util.Collection;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.compaction.BlobIdUpdater;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;
import org.apache.james.mailbox.cassandra.table.MessageIdToImapUid;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Updates Cassandra message references from old standalone blob IDs to compacted chunk slot IDs.
 *
 * <h3>Consistency Window and Crash Safety</h3>
 * Updates span multiple Cassandra tables:
 * <ul>
 *   <li>{@code messageV3} (primary message metadata table, keyed by {@code message_id})</li>
 *   <li>{@code messageIdToImapUid} (lookup table, keyed by {@code message_id})</li>
 *   <li>{@code messageIdTable} (mailbox message table, keyed by {@code mailbox_id})</li>
 * </ul>
 *
 * <p>Because these tables have different partition keys, updates cannot be executed in an atomic
 * Cassandra logged batch without significant cross-node write penalties. Therefore, statements
 * are executed asynchronously. If the process crashes mid-update:</p>
 * <ul>
 *   <li>Candidates whose updates did not succeed will NOT have their original blobs deleted
 *       by compaction algorithms.</li>
 *   <li>If {@code messageV3} is updated but denormalized index tables ({@code imapUid} / {@code messageIdTable})
 *       are not yet updated before a crash, a transient inconsistency window exists.</li>
 *   <li>This transient window is non-destructive and is strictly healed by running
 *       {@link CassandraBlobIdRepairer}.</li>
 * </ul>
 */
public class CassandraBlobIdUpdater implements BlobIdUpdater {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement selectMessageV3;
    private final PreparedStatement updateMessageV3Header;
    private final PreparedStatement updateMessageV3Body;
    private final PreparedStatement selectImapUidByMessageId;
    private final PreparedStatement updateImapUidHeader;
    private final PreparedStatement updateMessageIdTableHeader;

    @Inject
    public CassandraBlobIdUpdater(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.selectMessageV3 = session.prepare(selectFrom(CassandraMessageV3Table.TABLE_NAME)
            .columns(HEADER_CONTENT, BODY_CONTENT)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        this.updateMessageV3Header = session.prepare(update(CassandraMessageV3Table.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        this.updateMessageV3Body = session.prepare(update(CassandraMessageV3Table.TABLE_NAME)
            .set(setColumn(BODY_CONTENT, bindMarker(BODY_CONTENT)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        this.selectImapUidByMessageId = session.prepare(selectFrom(MessageIdToImapUid.TABLE_NAME)
            .columns(MAILBOX_ID, IMAP_UID, HEADER_CONTENT)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        this.updateImapUidHeader = session.prepare(update(MessageIdToImapUid.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());

        this.updateMessageIdTableHeader = session.prepare(update(CassandraMessageIdTable.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());
    }

    @Override
    public Mono<Void> replaceReferences(BlobId oldId, BlobId newId, Collection<String> messageIds) {
        String oldIdStr = oldId.asString();
        String newIdStr = newId.asString();

        return Flux.fromIterable(messageIds)
            .flatMap(messageIdStr -> updateMessageReferences(messageIdStr, oldIdStr, newIdStr), 16)
            .then();
    }

    private Mono<Void> updateMessageReferences(String messageIdStr, String oldIdStr, String newIdStr) {
        UUID messageUuid;
        try {
            messageUuid = UUID.fromString(messageIdStr);
        } catch (IllegalArgumentException e) {
            return Mono.empty();
        }

        Mono<Void> updateV3 = cassandraAsyncExecutor.executeSingleRow(
                selectMessageV3.bind().set(MESSAGE_ID, messageUuid, TypeCodecs.TIMEUUID))
            .flatMap(row -> {
                String header = row.get(HEADER_CONTENT, TypeCodecs.TEXT);
                String body = row.get(BODY_CONTENT, TypeCodecs.TEXT);
                Mono<Void> headerUpdate = (header != null && header.equals(oldIdStr))
                    ? cassandraAsyncExecutor.executeVoid(updateMessageV3Header.bind()
                        .set(HEADER_CONTENT, newIdStr, TypeCodecs.TEXT)
                        .set(MESSAGE_ID, messageUuid, TypeCodecs.TIMEUUID))
                    : Mono.empty();
                Mono<Void> bodyUpdate = (body != null && body.equals(oldIdStr))
                    ? cassandraAsyncExecutor.executeVoid(updateMessageV3Body.bind()
                        .set(BODY_CONTENT, newIdStr, TypeCodecs.TEXT)
                        .set(MESSAGE_ID, messageUuid, TypeCodecs.TIMEUUID))
                    : Mono.empty();
                return Flux.merge(headerUpdate, bodyUpdate).then();
            });

        Mono<Void> updateDenormalized = cassandraAsyncExecutor.executeRows(
                selectImapUidByMessageId.bind().set(MESSAGE_ID, messageUuid, TypeCodecs.TIMEUUID))
            .flatMap(row -> {
                String header = row.get(HEADER_CONTENT, TypeCodecs.TEXT);
                if (header != null && header.equals(oldIdStr)) {
                    UUID mailboxId = row.get(MAILBOX_ID, TypeCodecs.UUID);
                    Long imapUid = row.get(IMAP_UID, TypeCodecs.BIGINT);
                    Mono<Void> imapUidUpdate = cassandraAsyncExecutor.executeVoid(updateImapUidHeader.bind()
                        .set(HEADER_CONTENT, newIdStr, TypeCodecs.TEXT)
                        .set(MESSAGE_ID, messageUuid, TypeCodecs.TIMEUUID)
                        .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                        .set(IMAP_UID, imapUid, TypeCodecs.BIGINT));
                    Mono<Void> messageIdTableUpdate = cassandraAsyncExecutor.executeVoid(updateMessageIdTableHeader.bind()
                        .set(HEADER_CONTENT, newIdStr, TypeCodecs.TEXT)
                        .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                        .set(IMAP_UID, imapUid, TypeCodecs.BIGINT));
                    return Flux.merge(imapUidUpdate, messageIdTableUpdate).then();
                }
                return Mono.empty();
            })
            .then();

        return Flux.merge(updateV3, updateDenormalized).then();
    }
}
