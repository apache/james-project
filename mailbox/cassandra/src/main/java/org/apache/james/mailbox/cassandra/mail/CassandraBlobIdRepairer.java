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

import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.compaction.BlobIdRepairer;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;
import org.apache.james.mailbox.cassandra.table.MessageIdToImapUid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Mono;

public class CassandraBlobIdRepairer implements BlobIdRepairer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobIdRepairer.class);

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobId.Factory blobIdFactory;

    private final PreparedStatement scanMessageIdTable;
    private final PreparedStatement selectImapUidSingle;
    private final PreparedStatement updateMessageIdTableHeader;

    private final PreparedStatement scanImapUidTable;
    private final PreparedStatement selectMessageIdTableSingle;
    private final PreparedStatement updateImapUidTableHeader;

    private final PreparedStatement scanMessageV3;
    private final PreparedStatement selectImapUidByMessageId;
    private final PreparedStatement updateMessageV3Header;

    @Inject
    public CassandraBlobIdRepairer(CqlSession session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;

        this.scanMessageIdTable = session.prepare(selectFrom(CassandraMessageIdTable.TABLE_NAME)
            .columns(MAILBOX_ID, IMAP_UID, MESSAGE_ID, HEADER_CONTENT)
            .limit(1000)
            .build());

        this.selectImapUidSingle = session.prepare(selectFrom(MessageIdToImapUid.TABLE_NAME)
            .column(HEADER_CONTENT)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());

        this.updateMessageIdTableHeader = session.prepare(update(CassandraMessageIdTable.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());

        this.scanImapUidTable = session.prepare(selectFrom(MessageIdToImapUid.TABLE_NAME)
            .columns(MESSAGE_ID, MAILBOX_ID, IMAP_UID, HEADER_CONTENT)
            .limit(1000)
            .build());

        this.selectMessageIdTableSingle = session.prepare(selectFrom(CassandraMessageIdTable.TABLE_NAME)
            .column(HEADER_CONTENT)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());

        this.updateImapUidTableHeader = session.prepare(update(MessageIdToImapUid.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());

        this.scanMessageV3 = session.prepare(selectFrom(CassandraMessageV3Table.TABLE_NAME)
            .columns(MESSAGE_ID, HEADER_CONTENT, BODY_CONTENT)
            .limit(1000)
            .build());

        this.selectImapUidByMessageId = session.prepare(selectFrom(MessageIdToImapUid.TABLE_NAME)
            .column(HEADER_CONTENT)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        this.updateMessageV3Header = session.prepare(update(CassandraMessageV3Table.TABLE_NAME)
            .set(setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    @Override
    public Mono<BlobId> repair(BucketName bucketName, BlobId staleBlobId) {
        String staleStr = staleBlobId.asString();

        return repairFromMessageIdTable(staleStr)
            .switchIfEmpty(repairFromImapUidTable(staleStr))
            .switchIfEmpty(repairFromMessageV3(staleStr))
            .doOnNext(repairedId -> LOGGER.info("Successfully repaired stale blob ID {} with canonical ID {}", staleStr, repairedId.asString()));
    }

    private Mono<BlobId> repairFromMessageIdTable(String staleStr) {
        return cassandraAsyncExecutor.executeRows(scanMessageIdTable.bind())
            .filter(row -> staleStr.equals(row.get(HEADER_CONTENT, TypeCodecs.TEXT)))
            .concatMap(row -> {
                UUID mailboxId = row.get(MAILBOX_ID, TypeCodecs.UUID);
                Long imapUid = row.get(IMAP_UID, TypeCodecs.BIGINT);
                UUID messageId = row.get(MESSAGE_ID, TypeCodecs.TIMEUUID);

                return cassandraAsyncExecutor.executeSingleRow(selectImapUidSingle.bind()
                        .set(MESSAGE_ID, messageId, TypeCodecs.TIMEUUID)
                        .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                        .set(IMAP_UID, imapUid, TypeCodecs.BIGINT))
                    .flatMap(imapRow -> {
                        String canonical = imapRow.get(HEADER_CONTENT, TypeCodecs.TEXT);
                        if (canonical != null && !canonical.equals(staleStr)) {
                            return cassandraAsyncExecutor.executeVoid(updateMessageIdTableHeader.bind()
                                    .set(HEADER_CONTENT, canonical, TypeCodecs.TEXT)
                                    .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                                    .set(IMAP_UID, imapUid, TypeCodecs.BIGINT))
                                .thenReturn(blobIdFactory.parse(canonical));
                        }
                        return Mono.empty();
                    });
            })
            .next();
    }

    private Mono<BlobId> repairFromImapUidTable(String staleStr) {
        return cassandraAsyncExecutor.executeRows(scanImapUidTable.bind())
            .filter(row -> staleStr.equals(row.get(HEADER_CONTENT, TypeCodecs.TEXT)))
            .concatMap(row -> {
                UUID messageId = row.get(MESSAGE_ID, TypeCodecs.TIMEUUID);
                UUID mailboxId = row.get(MAILBOX_ID, TypeCodecs.UUID);
                Long imapUid = row.get(IMAP_UID, TypeCodecs.BIGINT);

                return cassandraAsyncExecutor.executeSingleRow(selectMessageIdTableSingle.bind()
                        .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                        .set(IMAP_UID, imapUid, TypeCodecs.BIGINT))
                    .flatMap(msgRow -> {
                        String canonical = msgRow.get(HEADER_CONTENT, TypeCodecs.TEXT);
                        if (canonical != null && !canonical.equals(staleStr)) {
                            return cassandraAsyncExecutor.executeVoid(updateImapUidTableHeader.bind()
                                    .set(HEADER_CONTENT, canonical, TypeCodecs.TEXT)
                                    .set(MESSAGE_ID, messageId, TypeCodecs.TIMEUUID)
                                    .set(MAILBOX_ID, mailboxId, TypeCodecs.UUID)
                                    .set(IMAP_UID, imapUid, TypeCodecs.BIGINT))
                                .thenReturn(blobIdFactory.parse(canonical));
                        }
                        return Mono.empty();
                    });
            })
            .next();
    }

    private Mono<BlobId> repairFromMessageV3(String staleStr) {
        return cassandraAsyncExecutor.executeRows(scanMessageV3.bind())
            .filter(row -> staleStr.equals(row.get(HEADER_CONTENT, TypeCodecs.TEXT)))
            .concatMap(row -> {
                UUID messageId = row.get(MESSAGE_ID, TypeCodecs.TIMEUUID);

                return cassandraAsyncExecutor.executeRows(selectImapUidByMessageId.bind()
                        .set(MESSAGE_ID, messageId, TypeCodecs.TIMEUUID))
                    .filter(imapRow -> {
                        String canonical = imapRow.get(HEADER_CONTENT, TypeCodecs.TEXT);
                        return canonical != null && !canonical.equals(staleStr);
                    })
                    .next()
                    .flatMap(imapRow -> {
                        String canonical = imapRow.get(HEADER_CONTENT, TypeCodecs.TEXT);
                        return cassandraAsyncExecutor.executeVoid(updateMessageV3Header.bind()
                                .set(HEADER_CONTENT, canonical, TypeCodecs.TEXT)
                                .set(MESSAGE_ID, messageId, TypeCodecs.TIMEUUID))
                            .thenReturn(blobIdFactory.parse(canonical));
                    });
            })
            .next();
    }
}
