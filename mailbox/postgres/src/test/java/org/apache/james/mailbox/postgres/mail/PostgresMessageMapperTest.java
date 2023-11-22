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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageMapperTest;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMessageMapperTest extends MessageMapperTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMessageMapper postgresMessageMapper;
    private UpdatableTickingClock updatableTickingClock;

    @Override
    protected MapperProvider createMapperProvider() {
        return null; // todo
    }

    @Override
    protected UpdatableTickingClock updatableTickingClock() {
        return updatableTickingClock;
    }

    @BeforeEach
    void setup() {
        PostgresExecutor postgresExecutor = postgresExtension.getPostgresExecutor();

        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(postgresExecutor);

        PostgresModSeqProvider modSeqProvider = new PostgresModSeqProvider(mailboxDAO);
        PostgresUidProvider uidProvider = new PostgresUidProvider(mailboxDAO);

        BlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, new HashBlobId.Factory());

        updatableTickingClock = new UpdatableTickingClock(Instant.parse("2007-12-03T10:15:30.00Z"));
        postgresMessageMapper = new PostgresMessageMapper(
            postgresExecutor,
            modSeqProvider,
            uidProvider,
            blobStore,
            new UpdatableTickingClock(Instant.now()));
    }
}
