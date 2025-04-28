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
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadService;
import org.apache.james.jmap.api.upload.UploadServiceContract;
import org.apache.james.jmap.api.upload.UploadServiceDefaultImpl;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresUploadServiceTest implements UploadServiceContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(PostgresUploadDataDefinition.MODULE, PostgresQuotaDataDefinition.MODULE));

    private PostgresUploadRepository uploadRepository;
    private PostgresUploadUsageRepository uploadUsageRepository;
    private UploadService testee;

    @BeforeEach
    void setUp() {
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        PostgresUploadDAO uploadDAO = new PostgresUploadDAO(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
        PostgresUploadDAO.Factory uploadFactory = new PostgresUploadDAO.Factory(blobIdFactory, postgresExtension.getExecutorFactory());
        uploadRepository = new PostgresUploadRepository(blobIdFactory, new MemoryBlobStoreDAO(), Clock.systemUTC(), uploadFactory, uploadDAO);
        uploadUsageRepository = new PostgresUploadUsageRepository(new PostgresQuotaCurrentValueDAO(postgresExtension.getDefaultPostgresExecutor()));
        testee = new UploadServiceDefaultImpl(uploadRepository, uploadUsageRepository, UploadServiceContract.TEST_CONFIGURATION());
    }

    @Override
    public UploadRepository uploadRepository() {
        return uploadRepository;
    }

    @Override
    public UploadUsageRepository uploadUsageRepository() {
        return uploadUsageRepository;
    }

    @Override
    public UploadService testee() {
        return testee;
    }


}
