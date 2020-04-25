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
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.STORAGE_INFORMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDeletedMessageMetadataVaultTest implements DeletedMessageMetadataVaultContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private DeletedMessageMetadataVault testee;
    private MetadataDAO metadataDAO;
    private StorageInformationDAO storageInformationDAO;
    private UserPerBucketDAO userPerBucketDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        DeletedMessageWithStorageInformationConverter dtoConverter = new DeletedMessageWithStorageInformationConverter(blobIdFactory, messageIdFactory, new InMemoryId.Factory());

        metadataDAO = spy(new MetadataDAO(cassandra.getConf(), messageIdFactory, new MetadataSerializer(dtoConverter)));
        storageInformationDAO = spy(new StorageInformationDAO(cassandra.getConf(), blobIdFactory));
        userPerBucketDAO = spy(new UserPerBucketDAO(cassandra.getConf()));

        testee = new CassandraDeletedMessageMetadataVault(metadataDAO, storageInformationDAO, userPerBucketDAO);
    }

    @Override
    public DeletedMessageMetadataVault metadataVault() {
        return testee;
    }

    @Nested
    class ConsistencyTest {
        @Test
        void listShouldNotReturnMessagesWhenStorageDAOFailed() {
            when(storageInformationDAO.referenceStorageInformation(USERNAME, MESSAGE_ID, STORAGE_INFORMATION))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void listShouldNotReturnMessagesWhenMetadataDAOFailed() {
            when(metadataDAO.store(DELETED_MESSAGE))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void listShouldReturnMessagesWhenUserPerBucketDAOFailed() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).contains(DELETED_MESSAGE);
        }

        @Test
        void retrieveStorageInformationShouldReturnMetadataWhenUserPerBucketDAOStoreFailed() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Optional<StorageInformation> maybeInfo = Mono.from(metadataVault().retrieveStorageInformation(DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .blockOptional();
            assertThat(maybeInfo).isPresent();
        }

        @Disabled("The bucket being not referenced, the entry will not be dropped. Note that this get corrected by next " +
            "metadata referenced for this user")
        @Test
        void removingBucketShouldCleanUpInvalidStateForList() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Disabled("The bucket being not referenced, the entry will not be dropped. Note that this get corrected by next " +
            "metadata referenced for this user")
        @Test
        void removingBucketShouldCleanUpInvalidStateForRetrievingMetadata() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Optional<StorageInformation> maybeInfo = Mono.from(metadataVault().retrieveStorageInformation(DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .blockOptional();
            assertThat(maybeInfo).isEmpty();
        }

        @Test
        void removingBucketShouldBeEventuallyConsistentForList() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }
            reset(userPerBucketDAO);
            Mono.from(testee.store(DELETED_MESSAGE_2)).block();

            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void removingBucketShouldBeEventuallyConsistentForMetadata() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }
            reset(userPerBucketDAO);
            Mono.from(testee.store(DELETED_MESSAGE_2)).block();

            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Optional<StorageInformation> maybeInfo = Mono.from(metadataVault().retrieveStorageInformation(DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .blockOptional();
            assertThat(maybeInfo).isEmpty();
        }

        @Test
        void directDeletionShouldCleanUpInvalidStateForList() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Mono.from(metadataVault().remove(BUCKET_NAME,
                DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void directDeletionShouldCleanUpInvalidStateForRetrievingMetadata() {
            when(userPerBucketDAO.addUser(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.store(DELETED_MESSAGE)).block();
            } catch (Exception e) {
                // ignored
            }

            Mono.from(metadataVault().remove(BUCKET_NAME,
                DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .block();

            Optional<StorageInformation> maybeInfo = Mono.from(metadataVault().retrieveStorageInformation(DELETED_MESSAGE.getDeletedMessage().getOwner(),
                DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .blockOptional();
            assertThat(maybeInfo).isEmpty();
        }

        @Test
        void retentionShouldBeRetriableWhenUserPerBucketDAOFails() {
            Mono.from(testee.store(DELETED_MESSAGE)).block();

            when(userPerBucketDAO.deleteBucket(BUCKET_NAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();
            } catch (Exception e) {
                // ignored
            }

            reset(userPerBucketDAO);
            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void retentionShouldBeRetriableWhenMetadataDAOFails() {
            Mono.from(testee.store(DELETED_MESSAGE)).block();

            when(metadataDAO.deleteInBucket(BUCKET_NAME, USERNAME))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();
            } catch (Exception e) {
                // ignored
            }

            reset(metadataDAO);
            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }

        @Test
        void retentionShouldBeRetriableWhenStorageInformationDAOFails() {
            Mono.from(testee.store(DELETED_MESSAGE)).block();

            when(storageInformationDAO.deleteStorageInformation(USERNAME, MESSAGE_ID))
                .thenReturn(Mono.error(new RuntimeException()));

            try {
                Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();
            } catch (Exception e) {
                // ignored
            }

            reset(storageInformationDAO);
            Mono.from(testee.removeMetadataRelatedToBucket(BUCKET_NAME)).block();

            Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USERNAME)).toStream();
            assertThat(messages).isEmpty();
        }
    }
}