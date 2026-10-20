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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class CassandraMessageMetadataReconcilerTest {
    private static final BlobId OLD = new PlainBlobId.Factory().of("old-header");
    private static final BlobId NEW = new PlainBlobId.Factory().of("new-header");
    private static final BlobId OTHER = new PlainBlobId.Factory().of("other-header");
    private static final Date INTERNAL_DATE = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
    private static final Date SAVE_DATE = Date.from(Instant.parse("2026-01-02T00:00:00Z"));

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        CassandraMessageDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE));

    private CassandraMessageIdDAO messageIds;
    private CassandraMessageIdToImapUidDAO imapUids;
    private CassandraMessageDAOV3 messages;
    private CassandraMessageMetadataReconciler testee;
    private CassandraMessageMetadata stale;
    private MessageRepresentation canonical;
    private CassandraId mailboxId;
    private CassandraMessageId messageId;
    private final MessageUid uid = MessageUid.of(1);

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxId = CassandraId.timeBased();
        messageId = new CassandraMessageId.Factory().generate();
        messageIds = new CassandraMessageIdDAO(cassandra.getConf(), new PlainBlobId.Factory());
        imapUids = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), new PlainBlobId.Factory(), CassandraConfiguration.DEFAULT_CONFIGURATION);
        messages = mock(CassandraMessageDAOV3.class);
        testee = new CassandraMessageMetadataReconciler(messages, messageIds, imapUids);
        stale = metadata(messageId, OLD);
        canonical = new MessageRepresentation(messageId, INTERNAL_DATE, 20L, 10, new ByteContent("headers".getBytes(UTF_8)),
            List.of(), NEW, new PlainBlobId.Factory().of("body"));
        when(messages.retrieveMessage(stale.getComposedMessageId(), FetchType.HEADERS)).thenReturn(Mono.just(canonical));
        messageIds.insert(stale).block();
        imapUids.insert(stale).block();
    }

    @Test
    void shouldReturnCanonicalMessageAndRepairBothIndexesWithoutChangingOtherMetadata() {
        MessageRepresentation result = testee.reconcile(stale).block();

        assertThat(result).isSameAs(canonical);
        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(metadata(messageId, NEW));
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(metadata(messageId, NEW));
    }

    @Test
    void shouldRepairOneStaleIndexWhenTheOtherIsAlreadyCurrent() {
        messageIds.replaceHeaderContent(stale.getComposedMessageId().getComposedMessageId(), OLD, NEW).block();

        testee.reconcile(stale).block();

        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(metadata(messageId, NEW));
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(metadata(messageId, NEW));
    }

    @Test
    void shouldNotRecreateDeletedSecondaryRows() {
        messageIds.delete(mailboxId, uid).block();
        imapUids.delete(messageId, mailboxId).block();

        testee.reconcile(stale).block();

        assertThat(messageIds.retrieve(mailboxId, uid).block()).isEmpty();
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).collectList().block()).isEmpty();
    }

    @Test
    void shouldNotOverwriteNewerHeaderReferences() {
        ComposedMessageId id = stale.getComposedMessageId().getComposedMessageId();
        messageIds.replaceHeaderContent(id, OLD, OTHER).block();
        imapUids.replaceHeaderContent(id, OLD, OTHER).block();

        testee.reconcile(stale).block();

        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(metadata(messageId, OTHER));
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(metadata(messageId, OTHER));
    }

    @Test
    void shouldNotRepairAnotherMessageAtTheSameMailboxUid() {
        CassandraMessageId replacement = new CassandraMessageId.Factory().generate();
        messageIds.delete(mailboxId, uid).block();
        messageIds.insert(metadata(replacement, OLD)).block();

        testee.reconcile(stale).block();

        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(metadata(replacement, OLD));
    }

    @Test
    void missingCanonicalBlobShouldPropagateAndLeaveIndexesUnchanged() {
        ObjectNotFoundException failure = new ObjectNotFoundException("canonical header missing");
        when(messages.retrieveMessage(stale.getComposedMessageId(), FetchType.HEADERS)).thenReturn(Mono.error(failure));

        assertThatThrownBy(() -> testee.reconcile(stale).block()).isSameAs(failure);

        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(stale);
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(stale);
    }

    @Test
    void repairFailureShouldPropagateAndAllowRetry(CassandraCluster cassandra) {
        cassandra.getConf().registerScenario(fail().times(1).whenQueryStartsWith("UPDATE imapuidtable SET headercontent"));

        assertThatThrownBy(() -> testee.reconcile(stale).block()).hasRootCauseMessage("Injected failure");

        assertThat(messageIds.retrieve(mailboxId, uid).block()).contains(metadata(messageId, NEW));
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(stale);
        testee.reconcile(stale).block();
        assertThat(imapUids.retrieve(messageId, Optional.of(mailboxId)).single().block()).isEqualTo(metadata(messageId, NEW));
    }

    private CassandraMessageMetadata metadata(CassandraMessageId id, BlobId header) {
        return CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, id, uid))
                .threadId(ThreadId.fromBaseMessageId(id))
                .flags(new Flags(Flags.Flag.SEEN))
                .modSeq(ModSeq.of(3))
                .build())
            .internalDate(INTERNAL_DATE)
            .saveDate(SAVE_DATE)
            .bodyStartOctet(10L)
            .size(20L)
            .headerContent(Optional.of(header))
            .build();
    }
}
