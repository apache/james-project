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

import static org.apache.james.vault.DeletedMessageFixture.NOW;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.DeletedMessageVaultSearchContract;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

public class BlobStoreDeletedMessageVaultV2Test implements DeletedMessageVaultContract, DeletedMessageVaultSearchContract.AllContracts {
    private BlobStoreDeletedMessageVaultV2 messageVault;
    private UpdatableTickingClock clock;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(NOW.toInstant());
        metricFactory = new RecordingMetricFactory();
        MemoryBlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        messageVault = new BlobStoreDeletedMessageVaultV2(metricFactory, new MemoryDeletedMessageMetadataVault(),
                BlobStoreFactory.builder()
                        .blobStoreDAO(blobStoreDAO)
                        .blobIdFactory(blobIdFactory)
                        .defaultBucketName()
                        .passthrough(),
                blobStoreDAO, new BlobIdTimeGenerator(blobIdFactory, clock), VaultConfiguration.ENABLED_DEFAULT);
    }

    @Override
    public DeletedMessageVault getVault() {
        return messageVault;
    }

    @Override
    public UpdatableTickingClock getClock() {
        return clock;
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
