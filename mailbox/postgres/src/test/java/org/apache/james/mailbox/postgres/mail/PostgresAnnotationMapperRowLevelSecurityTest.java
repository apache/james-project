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
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.DefaultPostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresAnnotationMapperRowLevelSecurityTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final Username BENWA = Username.of("benwa");
    protected static final MailboxPath benwaInboxPath = MailboxPath.forUser(BENWA, "INBOX");
    private static final MailboxSession aliceSession = MailboxSessionUtil.create(Username.of("alice@domain1"));
    private static final MailboxSession bobSession = MailboxSessionUtil.create(Username.of("bob@domain1"));
    private static final MailboxSession bobDomain2Session = MailboxSessionUtil.create(Username.of("bob@domain2"));
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMailboxSessionMapperFactory postgresMailboxSessionMapperFactory;
    private MailboxId mailboxId;

    private MailboxId generateMailboxId() {
        MailboxMapper mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
        return mailboxMapper.create(benwaInboxPath, UID_VALIDITY).block().getMailboxId();
    }

    @BeforeEach
    public void setUp() {
        BlobId.Factory blobIdFactory = new HashBlobId.Factory();
        postgresMailboxSessionMapperFactory = new PostgresMailboxSessionMapperFactory(new DefaultPostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(postgresExtension.getConnectionFactory())),
            new UpdatableTickingClock(Instant.now()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory),
            blobIdFactory);

        mailboxId = generateMailboxId();
    }

    @Test
    void annotationsCanBeAccessedAtTheDataLevelByMembersOfTheSameDomain() {
        postgresMailboxSessionMapperFactory.getAnnotationMapper(aliceSession).insertAnnotation(mailboxId, PRIVATE_ANNOTATION);

        assertThat(postgresMailboxSessionMapperFactory.getAnnotationMapper(bobSession).getAllAnnotations(mailboxId)).isNotEmpty();
    }

    @Test
    void annotationsShouldBeIsolatedByDomain() {
        postgresMailboxSessionMapperFactory.getAnnotationMapper(aliceSession).insertAnnotation(mailboxId, PRIVATE_ANNOTATION);

        assertThat(postgresMailboxSessionMapperFactory.getAnnotationMapper(bobDomain2Session).getAllAnnotations(mailboxId)).isEmpty();
    }
}
