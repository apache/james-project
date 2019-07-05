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

import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.DELETED_MESSAGE_2_OTHER_BUCKET;
import static org.apache.james.vault.metadata.DeletedMessageVaultMetadataFixture.OTHER_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeletedMessageMetadataVaultContract {

    DeletedMessageMetadataVault metadataVault();

    @Test
    default void listMessagesShouldBeEmptyWhenNoMessageInserted() {
        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).isEmpty();
    }

    @Test
    default void listMessagesShouldContainPreviouslyInsertedMessage() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).containsOnly(DELETED_MESSAGE);
    }

    @Test
    default void listMessagesShouldContainAllPreviouslyInsertedMessages() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).containsOnly(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    default void listMessagesShouldNotReturnMessagesOfOtherBuckets() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2_OTHER_BUCKET)).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).containsOnly(DELETED_MESSAGE);
    }

    @Test
    default void listBucketsShouldBeEmptyWhenNoMessageInserted() {
        Stream<BucketName> messages = Flux.from(metadataVault().listRelatedBuckets()).toStream();
        assertThat(messages).isEmpty();
    }

    @Test
    default void listBucketsShouldReturnAllUsedBuckets() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2_OTHER_BUCKET)).block();

        Stream<BucketName> messages = Flux.from(metadataVault().listRelatedBuckets()).toStream();
        assertThat(messages).containsOnly(BUCKET_NAME, OTHER_BUCKET_NAME);
    }

    @Test
    default void listBucketsShouldNotReturnDuplicates() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Stream<BucketName> messages = Flux.from(metadataVault().listRelatedBuckets()).toStream();
        assertThat(messages).containsExactly(BUCKET_NAME);
    }

    @Test
    default void listBucketsShouldStillListNotYetDeletedBuckets() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2_OTHER_BUCKET)).block();

        Mono.from(metadataVault().removeMetadataRelatedToBucket(BUCKET_NAME)).block();

        Stream<BucketName> messages = Flux.from(metadataVault().listRelatedBuckets()).toStream();
        assertThat(messages).containsOnly(OTHER_BUCKET_NAME);
    }

    @Test
    default void listBucketsShouldNotReturnDeletedBuckets() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Mono.from(metadataVault().removeMetadataRelatedToBucket(BUCKET_NAME)).block();

        Stream<BucketName> messages = Flux.from(metadataVault().listRelatedBuckets()).toStream();
        assertThat(messages).isEmpty();
    }

    @Test
    default void removeBucketShouldOnlyRemoveEntriesOfTheGivenBucket() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2_OTHER_BUCKET)).block();

        Mono.from(metadataVault().removeMetadataRelatedToBucket(BUCKET_NAME)).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(OTHER_BUCKET_NAME, USER)).toStream();
        assertThat(messages).containsOnly(DELETED_MESSAGE_2_OTHER_BUCKET);
    }

    @Test
    default void removeBucketShouldRemoveAllEntriesOfTheGivenBucket() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Mono.from(metadataVault().removeMetadataRelatedToBucket(BUCKET_NAME)).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).isEmpty();
    }

    @Test
    default void listMessagesShouldNotReturnRemovedItems() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Mono.from(metadataVault().remove(BUCKET_NAME, USER, DELETED_MESSAGE.getDeletedMessage().getMessageId())).block();

        Stream<DeletedMessageWithStorageInformation> messages = Flux.from(metadataVault().listMessages(BUCKET_NAME, USER)).toStream();
        assertThat(messages).containsExactly(DELETED_MESSAGE_2);
    }

    @Test
    default void removeShouldNotFailWhenTheMessageDoesNotExist() {
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        assertThatCode(() -> Mono.from(metadataVault()
                .remove(BUCKET_NAME, USER, DELETED_MESSAGE.getDeletedMessage().getMessageId()))
                .block())
            .doesNotThrowAnyException();
    }

    @Test
    default void retrieveStorageInformationShouldReturnStoredValue() {
        Mono.from(metadataVault().store(DELETED_MESSAGE)).block();

        StorageInformation storageInformation = Mono.from(metadataVault()
            .retrieveStorageInformation(USER, DELETED_MESSAGE.getDeletedMessage().getMessageId()))
            .block();

        assertThat(storageInformation).isEqualTo(DELETED_MESSAGE.getStorageInformation());
    }

    @Test
    default void retrieveStorageInformationShouldReturnEmptyWhenNotStored() {
        Mono.from(metadataVault().store(DELETED_MESSAGE_2)).block();

        Optional<StorageInformation> storageInformation = Mono.from(metadataVault()
            .retrieveStorageInformation(USER, DELETED_MESSAGE.getDeletedMessage().getMessageId()))
            .blockOptional();

        assertThat(storageInformation).isEmpty();
    }

    @Test
    default void retrieveStorageInformationShouldReturnEmptyWhenUserVaultIsEmpty() {
        Optional<StorageInformation> storageInformation = Mono.from(metadataVault()
            .retrieveStorageInformation(USER, DELETED_MESSAGE.getDeletedMessage().getMessageId()))
            .blockOptional();

        assertThat(storageInformation).isEmpty();
    }
}
