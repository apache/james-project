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

package org.apache.james.mailbox.cassandra.object.compaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

/**
 * Run-specific reference index.
 *
 * <p>Active runs update their table comment daily. Before a new run starts, tables without a heartbeat for three days
 * are removed. This assumes supervised deployments restart unresponsive James nodes within that period.</p>
 */
public class CassandraBlobReferenceIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobReferenceIndex.class);
    static final String TABLE_PREFIX = "blob_ref_";
    static final String HEARTBEAT_COMMENT_PREFIX = "blob-compaction-heartbeat:";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofDays(1);
    static final Duration STALE_AFTER = Duration.ofDays(3);
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(TABLE_PREFIX + "[0-9a-f]{32}");
    private static final int PAGE_SIZE = 500;
    private final CqlSession session;
    private final CassandraAsyncExecutor executor;
    private final int pageSize;
    private final Clock clock;
    private final Duration heartbeatInterval;
    private final Duration staleAfter;

    @Inject
    public CassandraBlobReferenceIndex(CqlSession session) {
        this(session, PAGE_SIZE, Clock.systemUTC(), HEARTBEAT_INTERVAL, STALE_AFTER);
    }

    @VisibleForTesting
    CassandraBlobReferenceIndex(CqlSession session, int pageSize) {
        this(session, pageSize, Clock.systemUTC(), HEARTBEAT_INTERVAL, STALE_AFTER);
    }

    @VisibleForTesting
    CassandraBlobReferenceIndex(CqlSession session, int pageSize, Clock clock, Duration heartbeatInterval, Duration staleAfter) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);
        this.pageSize = pageSize;
        this.clock = clock;
        this.heartbeatInterval = heartbeatInterval;
        this.staleAfter = staleAfter;
    }

    static String tableName(RunId runId) {
        return TABLE_PREFIX + runId.value().toString().replace("-", "");
    }

    Mono<CassandraBlobReferenceIndexDAO> create(RunId runId) {
        String table = tableName(runId);
        return cleanupStaleTables()
            // Cassandra 5: set allow_auto_snapshot = false once Cassandra 5 is the baseline (CASSANDRA-17842).
            // These temporary tables are dropped frequently and their automatic snapshots should not be retained.
            .then(changeSchema("CREATE TABLE IF NOT EXISTS " + table
                + " (blob_id text, reference_type text, reference_id uuid, PRIMARY KEY (blob_id, reference_type, reference_id))"
                + " WITH comment = '" + heartbeatComment(clock.instant()) + "'"))
            .then(Mono.defer(() -> prepare(table)));
    }

    private Mono<CassandraBlobReferenceIndexDAO> prepare(String table) {
        return Mono.fromCompletionStage(() -> session.prepareAsync(
                "INSERT INTO " + table + " (blob_id, reference_type, reference_id) VALUES (?, ?, ?)"))
            .flatMap(insert -> Mono.fromCompletionStage(() -> session.prepareAsync(
                    "SELECT reference_type, reference_id FROM " + table + " WHERE blob_id = ?"))
                .map(select -> new CassandraBlobReferenceIndexDAO(executor, insert, select, pageSize)));
    }

    Mono<Void> heartbeat(RunId runId) {
        return changeSchema("ALTER TABLE " + tableName(runId)
            + " WITH comment = '" + heartbeatComment(clock.instant()) + "'");
    }

    Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    Mono<Void> cleanupStaleTables() {
        Instant staleBefore = clock.instant().minus(staleAfter);
        String keyspace = session.getKeyspace().orElseThrow().asInternal();
        return executor.executeRows(SimpleStatement.newInstance(
                "SELECT table_name, comment FROM system_schema.tables WHERE keyspace_name = ?", keyspace))
            .filter(row -> isRunTable(row.getString("table_name")))
            .filter(row -> heartbeat(row.getString("comment"))
                .filter(instant -> instant.isBefore(staleBefore))
                .isPresent())
            .concatMap(row -> cleanup(row.getString("table_name")))
            .then();
    }

    public Mono<Void> cleanup(RunId runId) {
        return cleanup(tableName(runId));
    }

    private Mono<Void> cleanup(String table) {
        Preconditions.checkArgument(isRunTable(table), "Refusing to drop non-compaction table %s", table);
        return changeSchema("DROP TABLE IF EXISTS " + table)
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

    private Mono<Void> changeSchema(String query) {
        return executor.executeVoid(SimpleStatement.newInstance(query)
                .setExecutionProfile(JamesExecutionProfiles.getTableCreationProfile(session)))
            .then(Mono.fromCompletionStage(session::checkSchemaAgreementAsync))
            .flatMap(agreed -> agreed ? Mono.empty()
                : Mono.error(new IllegalStateException("No Cassandra schema agreement after " + query)));
    }
}
