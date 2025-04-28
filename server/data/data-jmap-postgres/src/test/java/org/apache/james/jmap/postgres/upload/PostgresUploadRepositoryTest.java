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

package org.apache.james.jmap.postgres.upload;

import java.time.Clock;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadRepositoryContract;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresUploadRepositoryTest implements UploadRepositoryContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(PostgresUploadDataDefinition.MODULE));
    private UploadRepository testee;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(Clock.systemUTC().instant());
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        PostgresUploadDAO uploadDAO = new PostgresUploadDAO(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
        PostgresUploadDAO.Factory uploadFactory = new PostgresUploadDAO.Factory(blobIdFactory, postgresExtension.getExecutorFactory());
        testee = new PostgresUploadRepository(blobIdFactory, new MemoryBlobStoreDAO(), clock, uploadFactory, uploadDAO);
    }

    @Override
    public UploadRepository testee() {
        return testee;
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }
}
