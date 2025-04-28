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

package org.apache.james.jmap.cassandra.upload;

import java.time.Clock;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadService;
import org.apache.james.jmap.api.upload.UploadServiceContract;
import org.apache.james.jmap.api.upload.UploadServiceDefaultImpl;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaDataDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraUploadServiceTest implements UploadServiceContract {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        UploadDataDefinition.MODULE, CassandraMailboxQuotaDataDefinition.MODULE, CassandraMutualizedQuotaDataDefinition.MODULE));

    private CassandraUploadRepository uploadRepository;
    private CassandraUploadUsageRepository uploadUsageRepository;
    private UploadService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandraCluster) {
        Clock clock = Clock.systemUTC();
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        uploadRepository = new CassandraUploadRepository(new UploadDAO(cassandraCluster.getConf(),
            blobIdFactory), blobIdFactory, new MemoryBlobStoreDAO(), clock);
        uploadUsageRepository = new CassandraUploadUsageRepository(new CassandraQuotaCurrentValueDao(cassandraCluster.getConf()));
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