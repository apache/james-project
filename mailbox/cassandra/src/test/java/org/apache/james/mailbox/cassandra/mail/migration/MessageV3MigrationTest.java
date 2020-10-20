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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class MessageV3MigrationTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);
    private static final List<MessageAttachmentMetadata> NO_ATTACHMENT = ImmutableList.of();

    public static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraMessageModule.MODULE,
        CassandraBlobModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMessageDAO daoV2;
    private CassandraMessageDAOV3 daoV3;
    private CassandraMessageId.Factory messageIdFactory;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf())
            .passthrough();
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        daoV2 = new CassandraMessageDAO(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            blobStore,
            blobIdFactory,
            new CassandraMessageId.Factory(),
            cassandraCluster.getCassandraConsistenciesConfiguration());
        daoV3 = new CassandraMessageDAOV3(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            blobStore,
            blobIdFactory,
            cassandraCluster.getCassandraConsistenciesConfiguration());
        messageIdFactory = new CassandraMessageId.Factory();
    }

    @Test
    void migrationTaskShouldMoveDataToMostRecentDao() throws Exception{
        SimpleMailboxMessage message1 = createMessage(messageIdFactory.generate());
        SimpleMailboxMessage message2 = createMessage(messageIdFactory.generate());
        SimpleMailboxMessage message3 = createMessage(messageIdFactory.generate());
        SimpleMailboxMessage message4 = createMessage(messageIdFactory.generate());

        daoV2.save(message1).block();
        daoV2.save(message2).block();
        daoV2.save(message3).block();
        daoV2.save(message4).block();

        new MessageV3Migration(daoV2, daoV3).apply();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(daoV3.retrieveMessage((CassandraMessageId) message1.getMessageId(), MessageMapper.FetchType.Metadata).block().getMessageId())
                .isEqualTo(message1.getMessageId());
            softly.assertThat(daoV3.retrieveMessage((CassandraMessageId) message2.getMessageId(), MessageMapper.FetchType.Metadata).block().getMessageId())
                .isEqualTo(message2.getMessageId());
            softly.assertThat(daoV3.retrieveMessage((CassandraMessageId) message3.getMessageId(), MessageMapper.FetchType.Metadata).block().getMessageId())
                .isEqualTo(message3.getMessageId());
            softly.assertThat(daoV3.retrieveMessage((CassandraMessageId) message4.getMessageId(), MessageMapper.FetchType.Metadata).block().getMessageId())
                .isEqualTo(message4.getMessageId());

            softly.assertThat(daoV2.list().collectList().block()).isEmpty();
        });
    }

    @Test
    void migrationTaskShouldPreserveMessageContent() throws Exception{
        SimpleMailboxMessage message1 = createMessage(messageIdFactory.generate());
        daoV2.save(message1).block();
        MessageRepresentation original = daoV2.retrieveMessage((CassandraMessageId) message1.getMessageId(), MessageMapper.FetchType.Metadata).block();

        new MessageV3Migration(daoV2, daoV3).apply();
        MessageRepresentation migrated = daoV3.retrieveMessage((CassandraMessageId) message1.getMessageId(), MessageMapper.FetchType.Metadata).block();

        int start = 0;
        int end = -1;
        assertThat(migrated).isEqualToComparingOnlyGivenFields(original, "messageId",
            "internalDate", "size", "bodyStartOctet", "properties", "attachments", "headerId", "bodyId");
        assertThat(migrated.getContent().newStream(start, end))
            .hasSameContentAs(original.getContent().newStream(start, end));
    }

    private SimpleMailboxMessage createMessage(MessageId messageId) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(MessageV3MigrationTest.BODY_START)
            .size(MessageV3MigrationTest.CONTENT.length())
            .content(new SharedByteArrayInputStream(MessageV3MigrationTest.CONTENT.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .properties(new PropertyBuilder().build())
            .addAttachments(NO_ATTACHMENT)
            .build();
    }
}