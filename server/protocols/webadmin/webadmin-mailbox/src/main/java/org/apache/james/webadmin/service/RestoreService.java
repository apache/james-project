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

package org.apache.james.webadmin.service;

import java.io.InputStream;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.mailbox.backup.MailboxBackup;
import org.apache.james.mailbox.backup.MailboxBackup.BackupStatus;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class RestoreService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreService.class);

    private final MailboxBackup mailboxBackup;
    private final BlobStore blobStore;

    @Inject
    public RestoreService(MailboxBackup mailboxBackup, BlobStore blobstore) {
        this.mailboxBackup = mailboxBackup;
        this.blobStore = blobstore;
    }

    public Mono<Task.Result> restore(Username username, BlobId blobId) {
        try (InputStream inputStream = blobStore.read(blobStore.getDefaultBucketName(), blobId)) {
            return restore(username, inputStream);
        } catch (Exception e) {
            LOGGER.error("Error restoring mailboxes for user {}", username.asString(), e);
            return Mono.just(Task.Result.PARTIAL);
        } finally {
            try {
                Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId)).block();
            } catch (Exception e) {
                LOGGER.error("Error deleting blob {} after restore", blobId.asString(), e);
            }
        }
    }

    private Mono<Task.Result> restore(Username username, InputStream source) {
        try {
            return Mono.from(mailboxBackup.restore(username, source))
                .map(this::computeTaskResult);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Task.Result computeTaskResult(BackupStatus status) {
        if (status == BackupStatus.DONE) {
            return Task.Result.COMPLETED;
        }
        return Task.Result.PARTIAL;
    }
}
