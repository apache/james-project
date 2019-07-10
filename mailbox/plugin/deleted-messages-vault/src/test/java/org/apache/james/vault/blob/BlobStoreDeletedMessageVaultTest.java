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
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.DeletedMessageFixture.OLD_DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZonedDateTime;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class BlobStoreDeletedMessageVaultTest implements DeletedMessageVaultContract {

    private BlobStoreDeletedMessageVault messageVault;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(NOW.toInstant());
        messageVault = new BlobStoreDeletedMessageVault(new MemoryDeletedMessageMetadataVault(),
            new MemoryBlobStore(new HashBlobId.Factory()),
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
        Mono.from(getVault().append(USER, OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        clock.setInstant(Instant.parse("2008-01-03T10:15:30.00Z"));
        Mono.from(getVault().append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        ZonedDateTime beginningOfRetention = ZonedDateTime.parse("2008-01-30T10:15:30.00Z");
        assertThat(messageVault.retentionQualifiedBuckets(beginningOfRetention).toStream())
            .containsOnly(BucketName.of("deleted-messages-2007-12-01"));
    }

    @Test
    void retentionQualifiedBucketsShouldReturnAllWhenAllBucketMonthAreBeforeBeginningOfRetention() {
        clock.setInstant(Instant.parse("2007-12-03T10:15:30.00Z"));
        Mono.from(getVault().append(USER, OLD_DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))).block();
        clock.setInstant(Instant.parse("2008-01-30T10:15:30.00Z"));
        Mono.from(getVault().append(USER, DELETED_MESSAGE_2, new ByteArrayInputStream(CONTENT))).block();

        assertThat(messageVault.retentionQualifiedBuckets(ZonedDateTime.parse("2008-02-01T10:15:30.00Z")).toStream())
            .containsOnly(
                BucketName.of("deleted-messages-2007-12-01"),
                BucketName.of("deleted-messages-2008-01-01"));
    }


    @Disabled("JAMES-2811 need vault.delete() to be implemented because this test uses that method")
    @Override
    public void deleteExpiredMessagesTaskShouldCompleteWhenAllMailsDeleted() {
    }

    @Disabled("Will be implemented later")
    @Override
    public void deleteShouldRunSuccessfullyInAConcurrentContext() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void searchAllShouldNotReturnDeletedItems() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void loadMimeMessageShouldReturnEmptyWhenDeleted() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void deleteShouldThrowOnNullMessageId() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void deleteShouldThrowOnNullUser() {

    }
}