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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateDataDefinition;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class PostgresBlobIdUpdaterTest {
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final BlobId OLD = BLOB_ID_FACTORY.of("old");
    private static final BlobId NEW = BLOB_ID_FACTORY.of("new");
    private static final BlobId OTHER = BLOB_ID_FACTORY.of("other");
    private static final int PAGE_SIZE = 2;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    private PostgresExecutor executor;
    private PostgresMessageDAO messageDAO;
    private PostgresBlobReferenceDAO referenceDAO;
    private PostgresBlobIdUpdater.Factory factory;

    @BeforeEach
    void setUp() {
        executor = postgresExtension.getByPassRLSPostgresExecutor();
        messageDAO = new PostgresMessageDAO(executor, BLOB_ID_FACTORY);
        referenceDAO = new PostgresBlobReferenceDAO(executor, BLOB_ID_FACTORY, PAGE_SIZE);
        factory = factory(new PostgresBlobReferenceIndex(executor));
    }

    @Test
    void shouldObserveAllReferencesAndOnlyReplacePredicateMatches() {
        UUID oldMessage = saveMessage(OLD);
        UUID otherMessage = saveMessage(OTHER);
        String attachment = saveAttachment(OLD);
        List<BlobId> observed = new ArrayList<>();

        BlobIdUpdater updater = factory.forPredicate(OLD::equals, observed::add).block();
        updater.replaceReferences(OTHER, NEW).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).containsExactlyInAnyOrder(OLD, OTHER, OLD);
        assertThat(messageBlobId(oldMessage)).contains(NEW.asString());
        assertThat(messageBlobId(otherMessage)).contains(OTHER.asString());
        assertThat(attachmentBlobId(attachment)).contains(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldReadEveryPageOnce() {
        List<UUID> messages = new ArrayList<>();
        List<String> attachments = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            messages.add(saveMessage(OLD));
            attachments.add(saveAttachment(OLD));
        }
        List<BlobId> observed = new ArrayList<>();
        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).hasSize(14).containsOnly(OLD);
        messages.forEach(id -> assertThat(messageBlobId(id)).contains(NEW.asString()));
        attachments.forEach(id -> assertThat(attachmentBlobId(id)).contains(NEW.asString()));
        updater.cleanup().block();
    }

    @Test
    void shouldPageAtExactPageSizeBoundary() {
        List<String> attachments = List.of(saveAttachment(OLD), saveAttachment(OLD));
        List<BlobId> observed = new ArrayList<>();
        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).hasSize(PAGE_SIZE);
        attachments.forEach(id -> assertThat(attachmentBlobId(id)).contains(NEW.asString()));
        updater.cleanup().block();
    }

    @Test
    void replacementShouldUseThePreparedSnapshot() {
        UUID message = saveMessage(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        UUID createdAfterSnapshot = saveMessage(OLD);

        updater.replaceReferences(OLD, NEW).block();

        assertThat(messageBlobId(message)).contains(NEW.asString());
        assertThat(messageBlobId(createdAfterSnapshot)).contains(OLD.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldBeIdempotentAndIgnoreUnknownIds() {
        UUID message = saveMessage(OLD);
        String attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();

        updater.replaceReferences(OTHER, NEW).block();
        updater.replaceReferences(OLD, NEW).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(messageBlobId(message)).contains(NEW.asString());
        assertThat(attachmentBlobId(attachment)).contains(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldNotOverwriteChangedReferences() {
        UUID message = saveMessage(OLD);
        String attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        execute("UPDATE message SET body_blob_id = $1 WHERE message_id = $2", OTHER.asString(), message);
        execute("UPDATE attachment SET blob_id = $1 WHERE id = $2", OTHER.asString(), attachment);

        updater.replaceReferences(OLD, NEW).block();

        assertThat(messageBlobId(message)).contains(OTHER.asString());
        assertThat(attachmentBlobId(attachment)).contains(OTHER.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldNotRecreateDeletedReferences() {
        UUID message = saveMessage(OLD);
        String attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        execute("DELETE FROM message WHERE message_id = $1", message);
        execute("DELETE FROM attachment WHERE id = $1", attachment);

        updater.replaceReferences(OLD, NEW).block();

        assertThat(messageBlobId(message)).isEmpty();
        assertThat(attachmentBlobId(attachment)).isEmpty();
        updater.cleanup().block();
    }

    @Test
    void failedReplacementShouldChangeNothingAndBeRetryable() {
        UUID message = saveMessage(OLD);
        String attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        execute("CREATE FUNCTION fail_attachment_update() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'Injected failure'; END; $$ LANGUAGE plpgsql");
        execute("CREATE TRIGGER fail_attachment_update BEFORE UPDATE ON attachment FOR EACH ROW EXECUTE FUNCTION fail_attachment_update()");

        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block()).hasMessageContaining("Injected failure");
        assertThat(messageBlobId(message)).contains(OLD.asString());
        assertThat(attachmentBlobId(attachment)).contains(OLD.asString());

        execute("DROP TRIGGER fail_attachment_update ON attachment");
        execute("DROP FUNCTION fail_attachment_update()");
        updater.replaceReferences(OLD, NEW).block();

        assertThat(messageBlobId(message)).contains(NEW.asString());
        assertThat(attachmentBlobId(attachment)).contains(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldIgnoreNullAttachmentBlobIds() {
        execute("INSERT INTO attachment (id, blob_id) VALUES ($1, NULL)", UUID.randomUUID().toString());
        List<BlobId> observed = new ArrayList<>();

        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        assertThat(observed).isEmpty();
        updater.cleanup().block();
    }

    @Test
    void shouldPrepareAndCleanEmptySnapshot() {
        List<BlobId> observed = new ArrayList<>();
        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        updater.replaceReferences(OLD, NEW).block();
        updater.cleanup().block();

        assertThat(observed).isEmpty();
        assertThat(runTables()).isEmpty();
    }

    @Test
    void cleanupShouldBeIdempotentAndRejectFurtherReplacements() {
        saveMessage(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        assertThat(runTables()).hasSize(1);

        updater.cleanup().block();
        updater.cleanup().block();

        assertThat(runTables()).isEmpty();
        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cleanupShouldLeaveOtherRunsIntact() {
        UUID message = saveMessage(OLD);
        BlobIdUpdater first = factory.forPredicate(id -> true, id -> { }).block();
        BlobIdUpdater second = factory.forPredicate(id -> true, id -> { }).block();
        assertThat(runTables()).hasSize(2);

        first.cleanup().block();
        second.replaceReferences(OLD, NEW).block();

        assertThat(runTables()).hasSize(1);
        assertThat(messageBlobId(message)).contains(NEW.asString());
        second.cleanup().block();
    }

    @Test
    void observerFailureShouldDropRunTable() {
        saveMessage(OLD);

        assertThatThrownBy(() -> factory.forPredicate(id -> true, id -> {
            throw new RuntimeException("observer failure");
        }).block()).hasMessage("observer failure");

        assertThat(runTables()).isEmpty();
    }

    @Test
    void predicateFailureShouldDropRunTable() {
        saveMessage(OLD);

        assertThatThrownBy(() -> factory.forPredicate(id -> {
            throw new RuntimeException("predicate failure");
        }, id -> { }).block()).hasMessage("predicate failure");

        assertThat(runTables()).isEmpty();
    }

    @Test
    void indexWriteFailureShouldDropRunTable() {
        saveMessage(OLD);
        PostgresBlobReferenceIndex index = new PostgresBlobReferenceIndex(executor) {
            @Override
            Mono<Void> store(RunId runId, List<PostgresBlobReferenceDAO.Reference> references) {
                return Mono.error(new RuntimeException("index write failure"));
            }
        };

        assertThatThrownBy(() -> factory(index).forPredicate(id -> true, id -> { }).block())
            .hasMessage("index write failure");

        assertThat(runTables()).isEmpty();
    }

    @Test
    void cancellationDuringPreparationShouldDropRunTable() throws InterruptedException {
        saveMessage(OLD);
        CountDownLatch observing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Disposable preparation = factory.forPredicate(id -> true, id -> {
                observing.countDown();
                awaitUninterruptibly(release);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        assertThat(observing.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(runTables()).hasSize(1);

        preparation.dispose();
        release.countDown();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(runTables()).isEmpty());
    }

    @Test
    void abandonedTableCanBeDroppedByRunId() {
        PostgresBlobReferenceIndex index = new PostgresBlobReferenceIndex(executor);
        RunId runId = RunId.generate();
        index.create(runId).block();

        index.cleanup(new RunId(UUID.fromString(runId.toString()))).block();

        assertThat(runTables()).isEmpty();
    }

    @Test
    void missingRunTableShouldFailReplacement() {
        saveMessage(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        runTables().forEach(table -> execute("DROP TABLE " + table));

        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block())
            .hasMessageContaining("does not exist");
        updater.cleanup().block();
    }

    @Test
    void creatingRunShouldCleanSnapshotsWithoutHeartbeatForThreeDays() {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        RunId stale = RunId.generate();
        index(now.minus(Duration.ofDays(4))).create(stale).block();

        BlobIdUpdater updater = factory(index(now)).forPredicate(id -> true, id -> { }).block();

        assertThat(runTables()).hasSize(1)
            .doesNotContain(PostgresBlobReferenceIndex.tableName(stale));
        updater.cleanup().block();
    }

    @Test
    void creatingRunShouldLeaveRecentAndUnrecognisedSnapshotsAlone() {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        RunId recent = RunId.generate();
        index(now.minus(Duration.ofDays(2))).create(recent).block();
        String unrecognised = PostgresBlobReferenceIndex.tableName(RunId.generate());
        execute("CREATE TABLE " + unrecognised + " (reference_blob_id varchar)");
        execute("COMMENT ON TABLE " + unrecognised + " IS 'something else'");
        String malformed = PostgresBlobReferenceIndex.tableName(RunId.generate());
        execute("CREATE TABLE " + malformed + " (reference_blob_id varchar)");
        execute("COMMENT ON TABLE " + malformed + " IS '" + PostgresBlobReferenceIndex.HEARTBEAT_COMMENT_PREFIX + "not-a-date'");
        String notARunTable = PostgresBlobReferenceIndex.TABLE_PREFIX + "other";
        execute("CREATE TABLE " + notARunTable + " (reference_blob_id varchar)");
        execute("COMMENT ON TABLE " + notARunTable + " IS '" + PostgresBlobReferenceIndex.HEARTBEAT_COMMENT_PREFIX + "2000-01-01T00:00:00Z'");

        BlobIdUpdater updater = factory(index(now)).forPredicate(id -> true, id -> { }).block();

        assertThat(runTables()).hasSize(4)
            .contains(PostgresBlobReferenceIndex.tableName(recent), unrecognised, malformed);
        assertThat(tableExists(notARunTable)).isTrue();
        updater.cleanup().block();
    }

    @Test
    void activeRunShouldRefreshHeartbeat() {
        List<Instant> instants = new CopyOnWriteArrayList<>(List.of(Instant.parse("2026-11-20T00:00:00Z")));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instants.get(instants.size() - 1);
            }
        };
        PostgresBlobReferenceIndex index = new PostgresBlobReferenceIndex(executor, clock, Duration.ofMillis(100), PostgresBlobReferenceIndex.STALE_AFTER);
        BlobIdUpdater updater = factory(index).forPredicate(id -> true, id -> { }).block();
        String table = runTables().get(0);
        assertThat(tableComment(table)).isEqualTo(PostgresBlobReferenceIndex.HEARTBEAT_COMMENT_PREFIX + "2026-11-20T00:00:00Z");

        instants.add(Instant.parse("2026-11-21T00:00:00Z"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(tableComment(table))
            .isEqualTo(PostgresBlobReferenceIndex.HEARTBEAT_COMMENT_PREFIX + "2026-11-21T00:00:00Z"));
        updater.cleanup().block();
    }

    @Test
    void heartbeatFailureShouldFailRunClosed() {
        saveMessage(OLD);
        PostgresBlobReferenceIndex index = new PostgresBlobReferenceIndex(executor, Clock.systemUTC(), Duration.ofMillis(100), PostgresBlobReferenceIndex.STALE_AFTER);
        BlobIdUpdater updater = factory(index).forPredicate(id -> true, id -> { }).block();
        runTables().forEach(table -> execute("DROP TABLE " + table));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Blob reference snapshot heartbeat failed"));
        updater.cleanup().block();
    }

    private PostgresBlobIdUpdater.Factory factory(PostgresBlobReferenceIndex index) {
        return new PostgresBlobIdUpdater.Factory(referenceDAO, index);
    }

    private PostgresBlobReferenceIndex index(Instant now) {
        return new PostgresBlobReferenceIndex(executor, Clock.fixed(now, ZoneOffset.UTC),
            PostgresBlobReferenceIndex.HEARTBEAT_INTERVAL, PostgresBlobReferenceIndex.STALE_AFTER);
    }

    private UUID saveMessage(BlobId bodyBlobId) {
        PostgresMessageId messageId = new PostgresMessageId.Factory().generate();
        String content = "Subject: test\n\nbody\n";
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .mailboxId(PostgresMailboxId.generate())
            .uid(MessageUid.of(1))
            .internalDate(new Date())
            .bodyStartOctet(15)
            .size(content.length())
            .content(new ByteContent(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .build();
        messageDAO.insert(message, bodyBlobId.asString()).block();
        return messageId.asUuid();
    }

    private String saveAttachment(BlobId blobId) {
        String id = UUID.randomUUID().toString();
        execute("INSERT INTO attachment (id, blob_id, type, size) VALUES ($1, $2, 'text/plain', 1)", id, blobId.asString());
        return id;
    }

    private Optional<String> messageBlobId(UUID messageId) {
        return selectString("SELECT body_blob_id FROM message WHERE message_id = $1", messageId);
    }

    private Optional<String> attachmentBlobId(String attachmentId) {
        return selectString("SELECT blob_id FROM attachment WHERE id = $1", attachmentId);
    }

    private String tableComment(String table) {
        return selectString("SELECT obj_description(to_regclass($1), 'pg_class')", table).orElse(null);
    }

    private boolean tableExists(String table) {
        return selectString("SELECT to_regclass($1)::text", table).isPresent();
    }

    private List<String> runTables() {
        return statement("SELECT tablename::text FROM pg_tables WHERE schemaname = current_schema() AND starts_with(tablename, $1)",
                PostgresBlobReferenceIndex.TABLE_PREFIX)
            .flatMap(result -> result.map((row, metadata) -> row.get(0, String.class)))
            .filter(table -> table.matches("blob_ref_[0-9a-f]{32}"))
            .collectList()
            .block();
    }

    private Optional<String> selectString(String sql, Object... bindings) {
        return statement(sql, bindings)
            .flatMap(result -> result.map((row, metadata) -> Optional.ofNullable(row.get(0, String.class))))
            .next()
            .blockOptional()
            .flatMap(value -> value);
    }

    private void execute(String sql, Object... bindings) {
        statement(sql, bindings)
            .flatMap(Result::getRowsUpdated)
            .then()
            .block();
    }

    private Flux<Result> statement(String sql, Object... bindings) {
        return postgresExtension.getConnection().flatMapMany(connection -> {
            Statement statement = connection.createStatement(sql);
            for (int i = 0; i < bindings.length; i++) {
                statement.bind(i, bindings[i]);
            }
            return statement.execute();
        });
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
