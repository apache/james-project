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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateDataDefinition;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RLSSupportPostgresMailboxMapperListTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final Username ALICE = Username.of("alice@domain1");
    private static final Username BOB = Username.of("bob@domain2");
    private static final MailboxSession ALICE_SESSION = MailboxSessionUtil.create(ALICE);
    private static final MailboxSession BOB_SESSION = MailboxSessionUtil.create(BOB);
    private static final MailboxSession NO_DOMAIN_SESSION = MailboxSessionUtil.create(Username.of("admin"));

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    private PostgresMailboxSessionMapperFactory postgresMailboxSessionMapperFactory;
    private Mailbox aliceInbox;
    private Mailbox bobInbox;

    @BeforeEach
    void setUp() {
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        postgresMailboxSessionMapperFactory = new PostgresMailboxSessionMapperFactory(postgresExtension.getExecutorFactory(),
            postgresExtension.getByPassRLSPostgresExecutor(),
            new UpdatableTickingClock(Instant.now()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory),
            blobIdFactory,
            postgresExtension.getPostgresConfiguration(),
            new AttachmentIdAssignationStrategy.Default(new StringBackedAttachmentIdFactory()));

        aliceInbox = postgresMailboxSessionMapperFactory.getMailboxMapper(ALICE_SESSION)
            .create(MailboxPath.inbox(ALICE), UID_VALIDITY).block();
        bobInbox = postgresMailboxSessionMapperFactory.getMailboxMapper(BOB_SESSION)
            .create(MailboxPath.inbox(BOB), UID_VALIDITY).block();
    }

    @Test
    void listShouldReturnMailboxesOfAllDomainsForASessionWithoutDomain() {
        assertThat(postgresMailboxSessionMapperFactory.getMailboxMapper(NO_DOMAIN_SESSION).list().collectList().block())
            .extracting(Mailbox::getMailboxId)
            .containsOnly(aliceInbox.getMailboxId(), bobInbox.getMailboxId());
    }

    @Test
    void listShouldReturnMailboxesOfAllDomainsForASessionWithADomain() {
        assertThat(postgresMailboxSessionMapperFactory.getMailboxMapper(ALICE_SESSION).list().collectList().block())
            .extracting(Mailbox::getMailboxId)
            .containsOnly(aliceInbox.getMailboxId(), bobInbox.getMailboxId());
    }
}
