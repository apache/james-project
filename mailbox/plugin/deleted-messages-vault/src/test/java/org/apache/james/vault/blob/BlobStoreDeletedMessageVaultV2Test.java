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

package org.apache.james.vault.blob;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_GENERATOR;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.james.vault.search.Query.ALL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.DeletedMessageVaultSearchContract;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BlobStoreDeletedMessageVaultV2Test implements DeletedMessageVaultContract, DeletedMessageVaultSearchContract.AllContracts {
    private BlobStoreDeletedMessageVault messageVaultV1;
    private BlobStoreDeletedMessageVaultV2 messageVaultV2;
    private UpdatableTickingClock clock;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(NOW.toInstant());
        metricFactory = new RecordingMetricFactory();
        MemoryDeletedMessageMetadataVault deletedMessageMetadataVault = new MemoryDeletedMessageMetadataVault();
        MemoryBlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        BlobStore blobStore = BlobStoreFactory.builder()
                .blobStoreDAO(blobStoreDAO)
                .blobIdFactory(new PlainBlobId.Factory())
                .defaultBucketName()
                .passthrough();
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();

        messageVaultV1 = new BlobStoreDeletedMessageVault(metricFactory, deletedMessageMetadataVault,
                blobStore, blobStoreDAO, new BucketNameGenerator(clock), clock, VaultConfiguration.ENABLED_DEFAULT);

        messageVaultV2 = new BlobStoreDeletedMessageVaultV2(metricFactory, deletedMessageMetadataVault,
                blobStore, blobStoreDAO, new BlobIdTimeGenerator(blobIdFactory, clock), VaultConfiguration.ENABLED_DEFAULT);
    }

    @Override
    public DeletedMessageVault getVault() {
        return messageVaultV2;
    }

    @Override
    public UpdatableTickingClock getClock() {
        return clock;
    }

    @Test
    public void loadMimeMessageShouldReturnOldMessage() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Mono.from(messageVaultV2.loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isNotEmpty()
            .satisfies(maybeContent -> assertThat(maybeContent.get()).hasSameContentAs(new ByteArrayInputStream(CONTENT)));
    }

    @Test
    public void loadMimeMessageShouldReturnEmptyWhenOldMessageDeleted() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        Mono.from(messageVaultV2.delete(USERNAME, MESSAGE_ID)).block();

        assertThat(Mono.from(messageVaultV2.loadMimeMessage(USERNAME, MESSAGE_ID)).blockOptional())
            .isEmpty();
    }

    @Test
    public void searchAllShouldReturnOldMessage() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(messageVaultV2.search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE);
    }

    @Test
    public void searchAllShouldReturnOldAndNewMessages() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(messageVaultV2.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(getVault().search(USERNAME, ALL)).collectList().block())
            .containsOnly(DELETED_MESSAGE, DELETED_MESSAGE_2);
    }

    @Test
    public void searchAllShouldSupportLimitQueryWithOldAndNewMessages() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(messageVaultV1.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        DeletedMessage deletedMessage3 = DELETED_MESSAGE_GENERATOR.apply(InMemoryMessageId.of(33).getRawId());
        Mono.from(messageVaultV2.append(deletedMessage3, new ByteArrayInputStream(CONTENT))).block();

        assertThat(Flux.from(messageVaultV2.search(USERNAME, Query.of(1, List.of()))).collectList().block())
            .hasSize(1);
        assertThat(Flux.from(messageVaultV2.search(USERNAME, Query.of(3, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
        assertThat(Flux.from(messageVaultV2.search(USERNAME, Query.of(4, List.of()))).collectList().block())
            .containsExactlyInAnyOrder(DELETED_MESSAGE, DELETED_MESSAGE_2, deletedMessage3);
    }

    @Test
    public void searchShouldReturnMatchingOldMessages() {
        Mono.from(messageVaultV1.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(messageVaultV1.append(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT))).block();

        assertThat(
            Flux.from(messageVaultV2.search(USERNAME,
                    Query.of(CriterionFactory.subject().containsIgnoreCase(SUBJECT))))
                .collectList().block())
            .containsOnly(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldCompleteWhenNoMail() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldDeleteOldMails() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldCompleteWhenAllMailsDeleted() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyRecentMails() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldDeleteOldMailsWhenRunSeveralTime() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldDoNothingWhenEmpty() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldNotDeleteRecentMails() {

    }

    @Override
    @Disabled("JAMES-4156: gc task for V2 not implemented yet")
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyOldMails() {

    }
}
