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

package org.apache.james.mailbox.cassandra.mail.migration;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class AttachmentV2Migration implements Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentV2Migration.class);
    private final CassandraAttachmentDAO attachmentDAOV1;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final BlobStore blobStore;

    @Inject
    public AttachmentV2Migration(CassandraAttachmentDAO attachmentDAOV1,
                                 CassandraAttachmentDAOV2 attachmentDAOV2,
                                 BlobStore blobStore) {
        this.attachmentDAOV1 = attachmentDAOV1;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobStore = blobStore;
    }

    @Override
    public Result run() {
        return attachmentDAOV1.retrieveAll()
            .flatMap(this::migrateAttachment)
            .reduce(Result.COMPLETED, Task::combine)
            .onErrorResume(this::handlError)
            .block();
    }

    private Mono<Result> handlError(Throwable throwable) {
        LOGGER.error("Error while performing attachmentDAO V2 migration", throwable);
        return Mono.just(Result.PARTIAL);
    }

    private Mono<Result> migrateAttachment(Attachment attachment) {
        return blobStore.save(BucketName.DEFAULT, attachment.getBytes())
            .map(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
            .flatMap(attachmentDAOV2::storeAttachment)
            .then(attachmentDAOV1.deleteAttachment(attachment.getAttachmentId()))
            .thenReturn(Result.COMPLETED);
    }
}
