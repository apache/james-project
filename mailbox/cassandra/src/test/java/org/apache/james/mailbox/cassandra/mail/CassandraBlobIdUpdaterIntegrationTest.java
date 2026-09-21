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

import static org.assertj.core.api.Assertions.assertThat;

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
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraBlobIdUpdaterIntegrationTest {
    public static final CassandraDataDefinition MODULES = CassandraDataDefinition.aggregateModules(
        CassandraMessageDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMessageId.Factory messageIdFactory;
    private PlainBlobId.Factory blobIdFactory;
    private CassandraMessageIdDAO messageIdDAO;
    private CassandraMessageIdToImapUidDAO imapUidDAO;
    private CassandraBlobIdUpdater testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        messageIdFactory = new CassandraMessageId.Factory();
        blobIdFactory = new PlainBlobId.Factory();
        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), blobIdFactory);
        imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), blobIdFactory, CassandraConfiguration.DEFAULT_CONFIGURATION);
        testee = new CassandraBlobIdUpdater(cassandra.getConf());
    }

    @Test
    void replaceReferencesShouldUpdateMessageV3AndDenormalizedTables(CassandraCluster cassandra) {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(100);

        BlobId originalHeaderBlobId = blobIdFactory.of("header-old-blob-id");
        BlobId originalBodyBlobId = blobIdFactory.of("body-old-blob-id");
        BlobId newHeaderSlotRef = blobIdFactory.of("chunk-123_chunk_456~0~100");
        BlobId newBodySlotRef = blobIdFactory.of("chunk-123_chunk_456~100~500");

        // Insert into messageV3
        cassandra.getConf().execute(
            "INSERT INTO " + CassandraMessageV3Table.TABLE_NAME
                + " (messageId, internalDate, bodyStartOctet, fullContentOctets, headerContent, bodyContent)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
            messageId.get(), Instant.now(), 10, 100L, originalHeaderBlobId.asString(), originalBodyBlobId.asString());

        // Insert into messageIdTable
        messageIdDAO.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(10L)
            .size(100L)
            .headerContent(Optional.of(originalHeaderBlobId))
            .build()).block();

        // Insert into imapUidTable
        imapUidDAO.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(10L)
            .size(100L)
            .headerContent(Optional.of(originalHeaderBlobId))
            .build()).block();

        // Replace header reference
        testee.replaceReferences(originalHeaderBlobId, newHeaderSlotRef, List.of(messageId.serialize())).block();

        // Replace body reference
        testee.replaceReferences(originalBodyBlobId, newBodySlotRef, List.of(messageId.serialize())).block();

        // Verify messageV3 has both new slot refs
        var row = cassandra.getConf().execute(
            "SELECT headerContent, bodyContent FROM " + CassandraMessageV3Table.TABLE_NAME + " WHERE messageId = ?",
            messageId.get()).one();
        assertThat(row.getString("headerContent")).isEqualTo(newHeaderSlotRef.asString());
        assertThat(row.getString("bodyContent")).isEqualTo(newBodySlotRef.asString());

        // Verify messageIdTable has new header slot ref
        CassandraMessageMetadata metaFromMessageIdTable = messageIdDAO.retrieve(mailboxId, messageUid).block().orElseThrow();
        assertThat(metaFromMessageIdTable.getHeaderContent()).contains(newHeaderSlotRef);

        // Verify imapUidTable has new header slot ref
        CassandraMessageMetadata metaFromImapUidTable = imapUidDAO.retrieve(messageId, Optional.of(mailboxId)).blockFirst();
        assertThat(metaFromImapUidTable.getHeaderContent()).contains(newHeaderSlotRef);
    }
}
