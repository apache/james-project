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

import java.time.Instant;

import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperACLTest;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.extension.RegisterExtension;

class RLSSupportPostgresMailboxMapperACLTest extends MailboxMapperACLTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresModule.aggregateModules(PostgresMailboxModule.MODULE,
        PostgresMailboxMemberModule.MODULE));

    @Override
    protected MailboxMapper createMailboxMapper() {
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        PostgresMailboxSessionMapperFactory postgresMailboxSessionMapperFactory = new PostgresMailboxSessionMapperFactory(postgresExtension.getExecutorFactory(),
            new UpdatableTickingClock(Instant.now()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory),
            blobIdFactory,
            PostgresConfiguration.builder().username("a").password("a").rowLevelSecurityEnabled().byPassRLSUser("b").byPassRLSPassword("b").build(),
            new AttachmentIdAssignationStrategy.Default(new StringBackedAttachmentIdFactory()));
        return postgresMailboxSessionMapperFactory.getMailboxMapper(MailboxSessionUtil.create(BENWA));
    }
}
