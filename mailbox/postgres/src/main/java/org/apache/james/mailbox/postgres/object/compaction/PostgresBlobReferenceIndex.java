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

package org.apache.james.mailbox.postgres.object.compaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.postgres.mail.PostgresAttachmentDataDefinition.PostgresAttachmentTable;
import org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable;
import org.apache.james.mailbox.postgres.object.compaction.PostgresBlobReferenceDAO.Reference;
import org.jooq.Field;
import org.jooq.InsertValuesStep3;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Run-specific reference index, stored in a regular table so that it survives a database crash.
 *
 * <p>Active runs update their table comment daily. Before a new run starts, tables without a heartbeat for three days
 * are removed. This assumes supervised deployments restart unresponsive James nodes within that period.</p>
 */
public class PostgresBlobReferenceIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresBlobReferenceIndex.class);
    static final String TABLE_PREFIX = "blob_ref_";
    static final String HEARTBEAT_COMMENT_PREFIX = "blob-compaction-heartbeat:";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofDays(1);
    static final Duration STALE_AFTER = Duration.ofDays(3);
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(TABLE_PREFIX + "[0-9a-f]{32}");

    // Distinct from mailbox column names so that UPDATE ... FROM conditions stay unambiguous.
    private static final Field<String> REFERENCE_BLOB_ID = DSL.field(DSL.name("reference_blob_id"), SQLDataType.VARCHAR.notNull());
    private static final Field<UUID> MESSAGE_REF = DSL.field(DSL.name("message_ref"), SQLDataType.UUID.nullable(true));
    private static final Field<String> ATTACHMENT_REF = DSL.field(DSL.name("attachment_ref"), SQLDataType.VARCHAR.nullable(true));
    private static final Field<String> RELATION_NAME = DSL.field(DSL.name("relname"), SQLDataType.VARCHAR);
    private static final Field<String> RELATION_COMMENT = DSL.field(DSL.name("comment"), SQLDataType.VARCHAR);

    private final PostgresExecutor executor;
    private final Clock clock;
    private final Duration heartbeatInterval;
    private final Duration staleAfter;

    @Inject
    public PostgresBlobReferenceIndex(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor executor) {
        this(executor, Clock.systemUTC(), HEARTBEAT_INTERVAL, STALE_AFTER);
    }

    @VisibleForTesting
    PostgresBlobReferenceIndex(PostgresExecutor executor, Clock clock, Duration heartbeatInterval, Duration staleAfter) {
        this.executor = executor;
        this.clock = clock;
        this.heartbeatInterval = heartbeatInterval;
        this.staleAfter = staleAfter;
    }

    static String tableName(RunId runId) {
        return TABLE_PREFIX + runId.value().toString().replace("-", "");
    }

    private static Table<Record> table(RunId runId) {
        return DSL.table(DSL.name(tableName(runId)));
    }

    Mono<Void> create(RunId runId) {
        Table<Record> table = table(runId);
        return cleanupStaleTables()
            .then(executor.executeVoid(dsl -> Mono.from(dsl.createTable(table)
                .column(REFERENCE_BLOB_ID)
                .column(MESSAGE_REF)
                .column(ATTACHMENT_REF))))
            .then(executor.executeVoid(dsl -> Mono.from(dsl.createIndex(DSL.name(tableName(runId) + "_blob_id"))
                .on(table, REFERENCE_BLOB_ID))))
            .then(heartbeat(runId));
    }

    Mono<Void> store(RunId runId, List<Reference> references) {
        if (references.isEmpty()) {
            return Mono.empty();
        }
        return executor.executeVoid(dsl -> {
            InsertValuesStep3<Record, String, UUID, String> insert = dsl.insertInto(table(runId), REFERENCE_BLOB_ID, MESSAGE_REF, ATTACHMENT_REF);
            references.forEach(reference -> insert.values(reference.blobId().asString(),
                reference.messageId().orElse(null),
                reference.attachmentId().orElse(null)));
            return Mono.from(insert);
        });
    }

    /**
     * Atomically replaces every indexed reference still pointing to {@code oldId}. Rows changed or deleted since the
     * snapshot are left untouched, so repeating a replacement is harmless.
     */
    Mono<Void> replace(RunId runId, BlobId oldId, BlobId newId) {
        Table<Record> table = table(runId);
        // The transaction commits only when the function emits a value: return the number of updated rows.
        return executor.executeTransaction(dsl -> Mono.from(dsl.update(MessageTable.TABLE_NAME)
                    .set(MessageTable.BODY_BLOB_ID, newId.asString())
                    .from(table)
                    .where(REFERENCE_BLOB_ID.eq(oldId.asString()))
                    .and(MessageTable.MESSAGE_ID.eq(MESSAGE_REF))
                    .and(MessageTable.BODY_BLOB_ID.eq(oldId.asString())))
                .flatMap(updatedMessages -> Mono.from(dsl.update(PostgresAttachmentTable.TABLE_NAME)
                    .set(PostgresAttachmentTable.BLOB_ID, newId.asString())
                    .from(table)
                    .where(REFERENCE_BLOB_ID.eq(oldId.asString()))
                    .and(PostgresAttachmentTable.ID.eq(ATTACHMENT_REF))
                    .and(PostgresAttachmentTable.BLOB_ID.eq(oldId.asString())))
                    .map(updatedAttachments -> updatedMessages + updatedAttachments)))
            .then();
    }

    Mono<Void> heartbeat(RunId runId) {
        return executor.executeVoid(dsl -> Mono.from(dsl.commentOnTable(table(runId))
            .is(heartbeatComment(clock.instant()))));
    }

    Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    Mono<Void> cleanupStaleTables() {
        Instant staleBefore = clock.instant().minus(staleAfter);
        return executor.executeRows(dsl -> Flux.from(dsl.resultQuery(
                    "SELECT c.relname, obj_description(c.oid, 'pg_class') AS comment"
                        + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname = current_schema() AND c.relkind = 'r' AND starts_with(c.relname, {0})",
                    DSL.val(TABLE_PREFIX))), PostgresExecutor.EAGER_FETCH)
            .filter(row -> isRunTable(row.get(RELATION_NAME)))
            .filter(row -> heartbeat(row.get(RELATION_COMMENT))
                .filter(instant -> instant.isBefore(staleBefore))
                .isPresent())
            .concatMap(row -> cleanup(row.get(RELATION_NAME)))
            .then();
    }

    public Mono<Void> cleanup(RunId runId) {
        return cleanup(tableName(runId));
    }

    private Mono<Void> cleanup(String table) {
        Preconditions.checkArgument(isRunTable(table), "Refusing to drop non-compaction table %s", table);
        return executor.executeVoid(dsl -> Mono.from(dsl.dropTableIfExists(DSL.name(table))))
            .doOnSuccess(any -> LOGGER.info("Cleaned blob reference snapshot {}", table));
    }

    private static boolean isRunTable(String table) {
        return table != null && TABLE_NAME_PATTERN.matcher(table).matches();
    }

    private static String heartbeatComment(Instant instant) {
        return HEARTBEAT_COMMENT_PREFIX + instant;
    }

    private static Optional<Instant> heartbeat(String comment) {
        if (comment == null || !comment.startsWith(HEARTBEAT_COMMENT_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(comment.substring(HEARTBEAT_COMMENT_PREFIX.length())));
        } catch (DateTimeParseException e) {
            LOGGER.warn("Ignoring malformed blob reference snapshot heartbeat: {}", comment);
            return Optional.empty();
        }
    }
}
