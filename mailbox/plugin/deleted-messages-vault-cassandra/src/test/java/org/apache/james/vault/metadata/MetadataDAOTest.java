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

package org.apache.james.vault.metadata;

import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE_2;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetadataDAOTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private MetadataDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        DeletedMessageWithStorageInformationConverter dtoConverter = new DeletedMessageWithStorageInformationConverter(
            new HashBlobId.Factory(), new InMemoryMessageId.Factory(), new InMemoryId.Factory());

        testee = new MetadataDAO(cassandra.getConf(), new InMemoryMessageId.Factory(), dtoConverter);
    }

    @Test
    void retrieveMessageIdsShouldReturnEmptyWhenNone() {
        Stream<MessageId> messageIds = testee.retrieveMessageIds(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }

    @Test
    void retrieveMessageIdsShouldReturnStoredMessageId() {
        testee.store(DELETED_MESSAGE).block();

        Stream<MessageId> messageIds = testee.retrieveMessageIds(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).containsExactly(DELETED_MESSAGE.getDeletedMessage().getMessageId());
    }

    @Test
    void retrieveMessageIdsShouldNotReturnDeletedMessages() {
        testee.store(DELETED_MESSAGE).block();

        testee.deleteInBucket(BUCKET_NAME, USER).block();

        Stream<MessageId> messageIds = testee.retrieveMessageIds(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }

    @Test
    void deleteInBucketShouldClearAllUserMessages() {
        testee.store(DELETED_MESSAGE).block();
        testee.store(DELETED_MESSAGE_2).block();

        testee.deleteInBucket(BUCKET_NAME, USER).block();

        Stream<MessageId> messageIds = testee.retrieveMessageIds(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }

    @Test
    void retrieveMessageIdsShouldReturnStoredMessageIds() {
        testee.store(DELETED_MESSAGE).block();
        testee.store(DELETED_MESSAGE_2).block();

        Stream<MessageId> messageIds = testee.retrieveMessageIds(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).containsExactlyInAnyOrder(
            DELETED_MESSAGE.getDeletedMessage().getMessageId(),
            DELETED_MESSAGE_2.getDeletedMessage().getMessageId());
    }

    @Test
    void retrieveMetadataShouldReturnEmptyWhenNone() {
        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }

    @Test
    void retrieveMetadataShouldReturnStoredMetadata() {
        testee.store(DELETED_MESSAGE).block();

        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).containsExactly(DELETED_MESSAGE);
    }

    @Test
    void retrieveMetadataShouldNotReturnDeletedMessages() {
        testee.store(DELETED_MESSAGE).block();

        testee.deleteInBucket(BUCKET_NAME, USER).block();

        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }

    @Test
    void retrieveMetadataShouldReturnAllStoredMetadata() {
        testee.store(DELETED_MESSAGE).block();
        testee.store(DELETED_MESSAGE_2).block();

        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    void deleteMessageShouldDeleteASingleMessage() {
        testee.store(DELETED_MESSAGE).block();
        testee.store(DELETED_MESSAGE_2).block();

        testee.deleteMessage(BUCKET_NAME, USER, MESSAGE_ID).block();

        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).containsExactlyInAnyOrder(DELETED_MESSAGE_2);
    }

    @Test
    void retrieveMetadataShouldNotReturnDeletedMetadata() {
        testee.store(DELETED_MESSAGE).block();

        testee.deleteMessage(BUCKET_NAME, USER, MESSAGE_ID).block();

        Stream<DeletedMessageWithStorageInformation> messageIds = testee.retrieveMetadata(BUCKET_NAME, USER).toStream();
        assertThat(messageIds).isEmpty();
    }
}