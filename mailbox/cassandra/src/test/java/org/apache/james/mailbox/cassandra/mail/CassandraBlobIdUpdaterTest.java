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

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobReferenceIndexDataDefinition;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

class CassandraBlobIdUpdaterTest {
    private static final BlobId OLD = new PlainBlobId.Factory().of("old");
    private static final BlobId NEW = new PlainBlobId.Factory().of("new");
    private static final BlobId OTHER = new PlainBlobId.Factory().of("other");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        CassandraMessageDataDefinition.MODULE,
        CassandraAttachmentDataDefinition.MODULE,
        CassandraBlobReferenceIndexDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE));

    private CqlSession session;
    private CassandraBlobIdUpdater.Factory factory;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        session = cassandra.getConf();
        factory = new CassandraBlobIdUpdater.Factory(
            new CassandraBlobReferenceDAO(session, new PlainBlobId.Factory(), 2),
            new CassandraBlobReferenceIndexDAO(session, 2));
    }

    @Test
    void shouldObserveAllReferencesAndOnlyReplacePredicateMatches() {
        UUID message = saveMessage(OLD, OTHER);
        UUID attachment = saveAttachment(OLD);
        List<BlobId> observed = new ArrayList<>();

        BlobIdUpdater updater = factory.forPredicate(OLD::equals, observed::add).block();
        updater.replaceReferences(OTHER, NEW).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).containsExactlyInAnyOrder(OLD, OTHER, OLD);
        assertThat(message(message).getString("headerContent")).isEqualTo(NEW.asString());
        assertThat(message(message).getString("bodyContent")).isEqualTo(OTHER.asString());
        assertThat(attachment(attachment).getString("blobId")).isEqualTo(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldReadEverySourceAndIndexPage() {
        List<UUID> messages = new ArrayList<>();
        List<UUID> attachments = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            messages.add(saveMessage(OLD, OLD));
            attachments.add(saveAttachment(OLD));
        }
        List<BlobId> observed = new ArrayList<>();
        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).hasSize(21).containsOnly(OLD);
        messages.forEach(id -> {
            assertThat(message(id).getString("headerContent")).isEqualTo(NEW.asString());
            assertThat(message(id).getString("bodyContent")).isEqualTo(NEW.asString());
        });
        attachments.forEach(id -> assertThat(attachment(id).getString("blobId")).isEqualTo(NEW.asString()));
        updater.cleanup().block();
    }

    @Test
    void replacementShouldNotRescanAuthoritativeTables(CassandraCluster cassandra) {
        saveMessage(OLD, OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        StatementRecorder recorder = cassandra.getConf().recordStatements();

        updater.replaceReferences(OLD, NEW).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(recorder.listExecutedStatements(StatementRecorder.Selector.preparedStatementStartingWith(
            "SELECT messageid,headercontent,bodycontent FROM messagev3"))).isEmpty();
        assertThat(recorder.listExecutedStatements(StatementRecorder.Selector.preparedStatementStartingWith(
            "SELECT idasuuid,blobid FROM attachmentv2"))).isEmpty();
        updater.cleanup().block();
    }

    @Test
    void shouldBeIdempotentAndIgnoreUnknownIds() {
        UUID message = saveMessage(OLD, OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();

        updater.replaceReferences(OTHER, NEW).block();
        updater.replaceReferences(OLD, NEW).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(message(message).getString("headerContent")).isEqualTo(NEW.asString());
        assertThat(message(message).getString("bodyContent")).isEqualTo(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldNotOverwriteChangedReferences() {
        UUID message = saveMessage(OLD, OLD);
        UUID attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        session.execute("UPDATE messageV3 SET headerContent=?, bodyContent=? WHERE messageId=?", OTHER.asString(), OTHER.asString(), message);
        session.execute("UPDATE attachmentV2 SET blobId=? WHERE idAsUUID=?", OTHER.asString(), attachment);

        updater.replaceReferences(OLD, NEW).block();

        assertThat(message(message).getString("headerContent")).isEqualTo(OTHER.asString());
        assertThat(message(message).getString("bodyContent")).isEqualTo(OTHER.asString());
        assertThat(attachment(attachment).getString("blobId")).isEqualTo(OTHER.asString());
        updater.cleanup().block();
    }

    @Test
    void shouldNotRecreateDeletedReferences() {
        UUID message = saveMessage(OLD, OLD);
        UUID attachment = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        session.execute("DELETE FROM messageV3 WHERE messageId=?", message);
        session.execute("DELETE FROM attachmentV2 WHERE idAsUUID=?", attachment);

        updater.replaceReferences(OLD, NEW).block();

        assertThat(message(message)).isNull();
        assertThat(attachment(attachment)).isNull();
        updater.cleanup().block();
    }

    @Test
    void shouldRetainSnapshotForRetryAfterPartialFailure(CassandraCluster cassandra) {
        UUID message = saveMessage(OLD, OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();
        cassandra.getConf().registerScenario(fail().times(1).whenQueryStartsWith("UPDATE messagev3 SET headercontent"));

        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block()).hasRootCauseMessage("Injected failure");
        assertThat(message(message).getString("bodyContent")).isEqualTo(NEW.asString());
        assertThat(message(message).getString("headerContent")).isEqualTo(OLD.asString());

        updater.replaceReferences(OLD, NEW).block();

        assertThat(message(message).getString("headerContent")).isEqualTo(NEW.asString());
        assertThat(message(message).getString("bodyContent")).isEqualTo(NEW.asString());
        updater.cleanup().block();
    }

    @Test
    void cleanupShouldBeIdempotentAndRejectFurtherReplacements() {
        saveMessage(OLD, OLD);
        BlobIdUpdater updater = factory.forPredicate(id -> true, id -> { }).block();

        updater.cleanup().block();
        updater.cleanup().block();

        assertThat(session.execute("SELECT * FROM blobReferenceIndex").all()).isEmpty();
        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cleaned up");
    }

    @Test
    void cleanupShouldLeaveOtherRunsIntactAcrossPages() {
        for (int i = 0; i < 5; i++) {
            saveMessage(new PlainBlobId.Factory().of("header-" + i), new PlainBlobId.Factory().of("body-" + i));
        }
        UUID target = saveMessage(OLD, OLD);
        BlobIdUpdater first = factory.forPredicate(id -> true, id -> { }).block();
        BlobIdUpdater second = factory.forPredicate(id -> true, id -> { }).block();

        first.cleanup().block();
        second.replaceReferences(OLD, NEW).block();

        assertThat(message(target).getString("headerContent")).isEqualTo(NEW.asString());
        second.cleanup().block();
        assertThat(session.execute("SELECT * FROM blobReferenceIndex").all()).isEmpty();
    }

    @Test
    void observerFailureShouldCleanPartiallyBuiltSnapshot() {
        UUID message = saveMessage(OLD, OLD);
        AtomicInteger observed = new AtomicInteger();

        assertThatThrownBy(() -> factory.forPredicate(id -> true, id -> {
            if (observed.incrementAndGet() == 2) {
                throw new IllegalStateException("observer failed");
            }
        }).block()).hasMessageContaining("observer failed");

        assertThat(session.execute("SELECT * FROM blobReferenceIndex").all()).isEmpty();
        assertThat(message(message).getString("headerContent")).isEqualTo(OLD.asString());
        assertThat(message(message).getString("bodyContent")).isEqualTo(OLD.asString());
    }

    @Test
    void shouldIgnoreNullBlobColumns() {
        UUID id = new CassandraMessageId.Factory().generate().get();
        session.execute("INSERT INTO messageV3 (messageId, headerContent) VALUES (?, ?)", id, OLD.asString());
        session.execute("INSERT INTO attachmentV2 (idAsUUID, id) VALUES (?, ?)", UUID.randomUUID(), "missing-blob");
        List<BlobId> observed = new ArrayList<>();

        BlobIdUpdater updater = factory.forPredicate(blobId -> true, observed::add).block();
        updater.replaceReferences(OLD, NEW).block();

        assertThat(observed).containsExactly(OLD);
        assertThat(message(id).getString("headerContent")).isEqualTo(NEW.asString());
        assertThat(message(id).getString("bodyContent")).isNull();
        updater.cleanup().block();
    }

    @Test
    void shouldKeepPermanentAttachmentReferencesPermanent() {
        UUID id = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(blobId -> true, blobId -> { }).block();

        updater.replaceReferences(OLD, NEW).block();

        Row row = session.execute("SELECT blobId, TTL(blobId) AS expiry FROM attachmentV2 WHERE idAsUUID=?", id).one();
        assertThat(row.getString("blobId")).isEqualTo(NEW.asString());
        assertThat(row.isNull("expiry")).isTrue();
        updater.cleanup().block();
    }

    @Test
    void shouldPreserveAttachmentExpiry() {
        UUID id = UUID.randomUUID();
        session.execute("INSERT INTO attachmentV2 (idAsUUID, blobId) VALUES (?, ?) USING TTL 120", id, OLD.asString());
        BlobIdUpdater updater = factory.forPredicate(blobId -> true, blobId -> { }).block();

        updater.replaceReferences(OLD, NEW).block();

        Row row = session.execute("SELECT blobId, TTL(blobId) AS expiry FROM attachmentV2 WHERE idAsUUID=?", id).one();
        assertThat(row.getString("blobId")).isEqualTo(NEW.asString());
        assertThat(row.getInt("expiry")).isBetween(1, 119);
        updater.cleanup().block();
    }

    @Test
    void nearlyExpiredAttachmentShouldNotBecomePermanent() {
        UUID id = saveAttachment(OLD);
        BlobIdUpdater updater = factory.forPredicate(blobId -> true, blobId -> { }).block();
        session.execute("UPDATE attachmentV2 USING TTL 1 SET blobId=? WHERE idAsUUID=?", OLD.asString(), id);

        try {
            updater.replaceReferences(OLD, NEW).block();
        } catch (IllegalStateException error) {
            assertThat(error).hasMessage("Attachment reference is expiring; retry relocation after expiry");
        }

        // Expiry can win the race with the read. Any reference still present must retain its original ID and TTL.
        Row row = session.execute("SELECT blobId, TTL(blobId) AS expiry FROM attachmentV2 WHERE idAsUUID=?", id).one();
        if (row != null && !row.isNull("blobId")) {
            assertThat(row.getString("blobId")).isEqualTo(OLD.asString());
            assertThat(row.isNull("expiry")).isFalse();
            assertThat(row.getInt("expiry")).isPositive();
        }
        updater.cleanup().block();
    }

    @Test
    void shouldPrepareAndCleanEmptySnapshot() {
        List<BlobId> observed = new ArrayList<>();
        BlobIdUpdater updater = factory.forPredicate(id -> true, observed::add).block();

        updater.replaceReferences(OLD, NEW).block();
        updater.cleanup().block();

        assertThat(observed).isEmpty();
        assertThat(session.execute("SELECT * FROM blobReferenceIndex").all()).isEmpty();
    }

    private UUID saveMessage(BlobId header, BlobId body) {
        UUID id = new CassandraMessageId.Factory().generate().get();
        session.execute("INSERT INTO messageV3 (messageId, headerContent, bodyContent) VALUES (?, ?, ?)",
            id, header.asString(), body.asString());
        return id;
    }

    private UUID saveAttachment(BlobId blobId) {
        UUID id = UUID.randomUUID();
        session.execute("INSERT INTO attachmentV2 (idAsUUID, blobId) VALUES (?, ?)", id, blobId.asString());
        return id;
    }

    private Row message(UUID id) {
        return session.execute("SELECT headerContent, bodyContent FROM messageV3 WHERE messageId=?", id).one();
    }

    private Row attachment(UUID id) {
        return session.execute("SELECT blobId FROM attachmentV2 WHERE idAsUUID=?", id).one();
    }
}
