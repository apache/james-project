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
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.DeletedMessageFixture.OLD_DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.james.vault.blob.BlobStoreDeletedMessageVault.APPEND_METRIC_NAME;
import static org.apache.james.vault.blob.BlobStoreDeletedMessageVault.DELETE_METRIC_NAME;
import static org.apache.james.vault.blob.BlobStoreDeletedMessageVault.LOAD_MIME_MESSAGE_METRIC_NAME;
import static org.apache.james.vault.blob.BlobStoreDeletedMessageVault.DELETE_EXPIRED_MESSAGES_METRIC_NAME;
import static org.apache.james.vault.blob.BlobStoreDeletedMessageVault.SEARCH_METRIC_NAME;
import static org.apache.james.vault.search.Query.ALL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZonedDateTime;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.DeletedMessageVaultSearchContract;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;


class BlobStoreDeletedMessageVaultTest implements DeletedMessageVaultContract, DeletedMessageVaultSearchContract.AllContracts {
    private BlobStoreDeletedMessageVault messageVault;
    private UpdatableTickingClock clock;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(NOW.toInstant());
        metricFactory = new RecordingMetricFactory();
        messageVault = new BlobStoreDeletedMessageVault(metricFactory, new MemoryDeletedMessageMetadataVault(),
            new MemoryBlobStore(new HashBlobId.Factory(), new MemoryDumbBlobStore()),
            new BucketNameGenerator(clock), clock, RetentionConfiguration.DEFAULT);
    }

    @Override
    public DeletedMessageVault getVault() {
        return messageVault;
    }

    @Override
    public UpdatableTickingClock getClock() {
        return clock;
    }

    @Test
    void retentionQualifiedBucketsShouldReturnOnlyBucketsFullyBeforeBeginningOfRetentionPeriod() {
        clock.setInstant(Instant.parse("2007-12-03T10:15:30.00Z"));
        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        clock.setInstant(Instant.parse("2008-01-03T10:15:30.00Z"));
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        ZonedDateTime beginningOfRetention = ZonedDateTime.parse("2008-01-30T10:15:30.00Z");
        assertThat(messageVault.retentionQualifiedBuckets(beginningOfRetention).toStream())
            .containsOnly(BucketName.of("deleted-messages-2007-12-01"));
    }

    @Test
    void retentionQualifiedBucketsShouldReturnAllWhenAllBucketMonthAreBeforeBeginningOfRetention() {
        clock.setInstant(Instant.parse("2007-12-03T10:15:30.00Z"));
        Mono.from(getVault().append(OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        clock.setInstant(Instant.parse("2008-01-30T10:15:30.00Z"));
        Mono.from(getVault().append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        assertThat(messageVault.retentionQualifiedBuckets(ZonedDateTime.parse("2008-02-01T10:15:30.00Z")).toStream())
            .containsOnly(
                BucketName.of("deleted-messages-2007-12-01"),
                BucketName.of("deleted-messages-2008-01-01"));
    }

    @Test
    void appendShouldPublishAppendTimerMetrics() {
        Mono.from(messageVault.append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT)))
            .block();
        Mono.from(messageVault.append(DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT)))
            .block();

        assertThat(metricFactory.executionTimesFor(APPEND_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void searchShouldPublishSearchTimerMetrics() {
        Mono.from(messageVault.search(USERNAME, ALL))
            .block();
        Mono.from(messageVault.search(USERNAME, ALL))
            .block();

        assertThat(metricFactory.executionTimesFor(SEARCH_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void loadMimeMessageShouldPublishLoadMimeMessageTimerMetrics() {
        Mono.from(messageVault.loadMimeMessage(USERNAME, MESSAGE_ID))
            .block();
        Mono.from(messageVault.loadMimeMessage(USERNAME, MESSAGE_ID))
            .block();

        assertThat(metricFactory.executionTimesFor(LOAD_MIME_MESSAGE_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void deleteShouldPublishDeleteTimerMetrics() {
        Mono.from(messageVault.delete(USERNAME, MESSAGE_ID))
            .block();
        Mono.from(messageVault.delete(USERNAME, MESSAGE_ID))
            .block();

        assertThat(metricFactory.executionTimesFor(DELETE_METRIC_NAME))
            .hasSize(2);
    }

    @Test
    void deleteExpiredMessagesTaskShouldPublishRetentionTimerMetrics() throws Exception {
        Mono.from(getVault().append(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        Mono.from(getVault().delete(USERNAME, DELETED_MESSAGE.getMessageId())).block();

        getVault().deleteExpiredMessagesTask().run();

        assertThat(metricFactory.executionTimesFor(DELETE_EXPIRED_MESSAGES_METRIC_NAME))
            .hasSize(1);
    }
}