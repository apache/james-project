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

package org.apache.james.mailbox.postgres.mail;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.AttachmentMapperTest;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresAttachmentMapperTest extends AttachmentMapperTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresAttachmentModule.MODULE);

    static BlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @Override
    protected AttachmentMapper createAttachmentMapper() {
        PostgresAttachmentDAO postgresAttachmentDAO = new PostgresAttachmentDAO(postgresExtension.getPostgresExecutor(), BLOB_ID_FACTORY);
        BlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, BLOB_ID_FACTORY);
        return new PostgresAttachmentMapper(postgresAttachmentDAO, blobStore);
    }

    @Override
    protected MessageId generateMessageId() {
        return new PostgresMessageId.Factory().generate();
    }
}
