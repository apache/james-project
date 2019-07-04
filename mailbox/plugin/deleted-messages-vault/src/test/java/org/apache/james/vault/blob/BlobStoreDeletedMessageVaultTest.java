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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultContract;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

class BlobStoreDeletedMessageVaultTest implements DeletedMessageVaultContract {
    private static final Instant NOW = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private BlobStoreDeletedMessageVault messageVault;

    @BeforeEach
    void setUp() {
        messageVault = new BlobStoreDeletedMessageVault(new MemoryDeletedMessageMetadataVault(),
            new MemoryBlobStore(new HashBlobId.Factory()),
            new BucketNameGenerator(CLOCK));
    }

    @Override
    public DeletedMessageVault getVault() {
        return messageVault;
    }


    @Disabled("Will be implemented later")
    public void deleteShouldThrowOnNullMessageId() {

    }

    @Disabled("Will be implemented later")
    public void deleteShouldThrowOnNullUser() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldCompleteWhenNoMail() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldCompleteWhenAllMailsDeleted() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyRecentMails() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldDeleteOldMails() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldNotDeleteRecentMails() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldDoNothingWhenEmpty() {

    }

    @Disabled("Will be implemented in JAMES-2811")
    @Override
    public void deleteExpiredMessagesTaskShouldCompleteWhenOnlyOldMails() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void deleteShouldRunSuccessfullyInAConcurrentContext() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void usersWithVaultShouldReturnEmptyWhenNoItem() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void usersWithVaultShouldReturnAllUsers() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void searchAllShouldNotReturnDeletedItems() {

    }

    @Disabled("Will be implemented later")
    @Override
    public void loadMimeMessageShouldReturnEmptyWhenDeleted() {

    }
}