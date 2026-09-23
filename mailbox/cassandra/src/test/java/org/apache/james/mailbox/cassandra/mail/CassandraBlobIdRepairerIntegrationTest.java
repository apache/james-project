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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.blob.compaction.ChunkFooter;
import org.apache.james.blob.compaction.ChunkFormat;
import org.apache.james.blob.compaction.ChunkId;
import org.apache.james.blob.compaction.ChunkedBlobStoreDAO;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.luben.zstd.Zstd;

import reactor.core.publisher.Mono;

class CassandraBlobIdRepairerIntegrationTest {
    public static final CassandraDataDefinition MODULES = CassandraDataDefinition.aggregateModules(
        CassandraMessageDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE);

    private static final BucketName TEST_BUCKET = BucketName.of("test-bucket");
    private static final GenerationAwareBlobId.Configuration CONFIG =
        new GenerationAwareBlobId.Configuration(1, Duration.ofDays(30));

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMessageId.Factory messageIdFactory;
    private PlainBlobId.Factory blobIdFactory;
    private CassandraMessageIdDAO messageIdDAO;
    private CassandraMessageIdToImapUidDAO imapUidDAO;
    private MemoryBlobStoreDAO rawStore;
    private ChunkedBlobStoreDAO chunkedBlobStoreDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        messageIdFactory = new CassandraMessageId.Factory();
        blobIdFactory = new PlainBlobId.Factory();
        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), blobIdFactory);
        imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), blobIdFactory, CassandraConfiguration.DEFAULT_CONFIGURATION);
        rawStore = new MemoryBlobStoreDAO();

        CassandraBlobIdRepairer repairer = new CassandraBlobIdRepairer(cassandra.getConf(), blobIdFactory);
        chunkedBlobStoreDAO = new ChunkedBlobStoreDAO(rawStore, rawStore, Optional.of(repairer));
    }

    @Test
    void repairShouldFixCorruptedMessageIdTableFromImapUidTable() throws Exception {
        byte[] content = "Subject: Hello\n\nThis is the email body".getBytes(StandardCharsets.UTF_8);

        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));
        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 555);
        Mono.from(rawStore.save(TEST_BUCKET, baseChunk.chunkBlobId(), BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long offset = footer.slotStarts().get(0);
        long limit = footer.slotLength(0);
        ChunkId validSlotRef = ChunkId.slotRef(baseChunk, offset, limit);
        ChunkId deadSlotRef = ChunkId.slotRef(baseChunk, 999999L, 100L);

        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(42);

        // Put valid slot in imapUidTable
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
            .headerContent(Optional.of(validSlotRef))
            .build()).block();

        // Corrupt messageIdTable with deadSlotRef
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
            .headerContent(Optional.of(deadSlotRef))
            .build()).block();

        // Read using deadSlotRef: triggers repair -> fetches canonical from imapUidTable -> fixes messageIdTable
        BlobStoreDAO.BytesBlob readBlob = Mono.from(chunkedBlobStoreDAO.readBytes(TEST_BUCKET, deadSlotRef)).block();
        byte[] readBack = Zstd.decompress(readBlob.payload(), content.length);
        assertThat(readBack).isEqualTo(content);

        // Verify messageIdTable is now repaired with the canonical validSlotRef
        CassandraMessageMetadata repairedMetadata = messageIdDAO.retrieve(mailboxId, messageUid).block().orElseThrow();
        assertThat(repairedMetadata.getHeaderContent().map(BlobId::asString)).contains(validSlotRef.asString());
    }

    @Test
    void repairShouldFixCorruptedImapUidTableFromMessageIdTable() throws Exception {
        byte[] content = "Subject: Another Test\n\nSecond body".getBytes(StandardCharsets.UTF_8);

        byte[] chunkBytes = ChunkFormat.writeToBytes(List.of(ChunkFormat.BlobSlotContent.of(content)));
        ChunkId baseChunk = ChunkId.ofChunk(CONFIG, 777);
        Mono.from(rawStore.save(TEST_BUCKET, baseChunk.chunkBlobId(), BlobStoreDAO.BytesBlob.of(chunkBytes))).block();

        ChunkFooter footer = ChunkFormat.readFooter(chunkBytes);
        long offset = footer.slotStarts().get(0);
        long limit = footer.slotLength(0);
        ChunkId validSlotRef = ChunkId.slotRef(baseChunk, offset, limit);
        ChunkId deadSlotRef = ChunkId.slotRef(baseChunk, 888888L, 200L);

        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(99);

        // Put valid slot in messageIdTable
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
            .headerContent(Optional.of(validSlotRef))
            .build()).block();

        // Corrupt imapUidTable with deadSlotRef
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
            .headerContent(Optional.of(deadSlotRef))
            .build()).block();

        // Read using deadSlotRef: triggers repair -> fetches canonical from messageIdTable -> fixes imapUidTable
        BlobStoreDAO.BytesBlob readBlob = Mono.from(chunkedBlobStoreDAO.readBytes(TEST_BUCKET, deadSlotRef)).block();
        byte[] readBack = Zstd.decompress(readBlob.payload(), content.length);
        assertThat(readBack).isEqualTo(content);

        // Verify imapUidTable is now repaired with the canonical validSlotRef
        CassandraMessageMetadata repairedMetadata = imapUidDAO.retrieve(messageId, Optional.of(mailboxId)).blockFirst();
        assertThat(repairedMetadata.getHeaderContent().map(BlobId::asString)).contains(validSlotRef.asString());
    }
}
