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

import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class StorageInformationDAOTest {
    private static final BucketName BUCKET_NAME = BucketName.of("deletedMessages-2019-06-01");
    private static final BucketName BUCKET_NAME_2 = BucketName.of("deletedMessages-2019-07-01");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final User OWNER = User.fromUsername("owner");
    private static final TestMessageId MESSAGE_ID = TestMessageId.of(36);
    private static final BlobId BLOB_ID = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d23");
    private static final BlobId BLOB_ID_2 = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d24");
    private static final StorageInformation STORAGE_INFORMATION = StorageInformation.builder().bucketName(BUCKET_NAME).blobId(BLOB_ID);
    private static final StorageInformation STORAGE_INFORMATION_2 = StorageInformation.builder().bucketName(BUCKET_NAME_2).blobId(BLOB_ID_2);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private StorageInformationDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new StorageInformationDAO(cassandra.getConf(), BLOB_ID_FACTORY);
    }

    @Test
    void retrieveStorageInformationShouldReturnEmptyWhenNone() {
        Optional<StorageInformation> storageInformation = testee.retrieveStorageInformation(OWNER, MESSAGE_ID).blockOptional();

        assertThat(storageInformation).isEmpty();
    }

    @Test
    void retrieveStorageInformationShouldReturnAddedValue() {
        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION).block();

        Optional<StorageInformation> storageInformation = testee.retrieveStorageInformation(OWNER, MESSAGE_ID).blockOptional();
        assertThat(storageInformation).contains(STORAGE_INFORMATION);
    }

    @Test
    void retrieveStorageInformationShouldReturnLatestAddedValue() {
        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION).block();

        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION_2).block();

        Optional<StorageInformation> storageInformation = testee.retrieveStorageInformation(OWNER, MESSAGE_ID).blockOptional();
        assertThat(storageInformation).contains(STORAGE_INFORMATION_2);
    }

    @Test
    void retrieveStorageInformationShouldReturnEmptyWhenDeleted() {
        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION).block();

        testee.deleteStorageInformation(OWNER, MESSAGE_ID).block();

        Optional<StorageInformation> storageInformation = testee.retrieveStorageInformation(OWNER, MESSAGE_ID).blockOptional();
        assertThat(storageInformation).isEmpty();
    }


    @Test
    void referenceStorageInformationShouldBeAllowedAfterADelete() {
        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION).block();

        testee.deleteStorageInformation(OWNER, MESSAGE_ID).block();

        testee.referenceStorageInformation(OWNER, MESSAGE_ID, STORAGE_INFORMATION).block();

        Optional<StorageInformation> storageInformation = testee.retrieveStorageInformation(OWNER, MESSAGE_ID).blockOptional();
        assertThat(storageInformation).contains(STORAGE_INFORMATION);
    }

    @Test
    void deleteStorageInformationShouldNotThrowWhenNone() {
        assertThatCode(() -> testee.deleteStorageInformation(OWNER, MESSAGE_ID).block())
            .doesNotThrowAnyException();
    }
}