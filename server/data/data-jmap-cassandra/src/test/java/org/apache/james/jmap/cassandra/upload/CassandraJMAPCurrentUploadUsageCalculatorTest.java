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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.JMAPCurrentUploadUsageCalculator;
import org.apache.james.jmap.api.upload.JMAPCurrentUploadUsageCalculatorContract;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraJMAPCurrentUploadUsageCalculatorTest implements JMAPCurrentUploadUsageCalculatorContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(UploadModule.MODULE, CassandraMutualizedQuotaModule.MODULE));

    private UploadRepository uploadRepository;
    private UploadUsageRepository uploadUsageRepository;
    private JMAPCurrentUploadUsageCalculator jmapCurrentUploadUsageCalculator;

    @BeforeEach
    private void setup() {
        Clock clock = Clock.systemUTC();
        uploadRepository = new CassandraUploadRepository(new UploadDAO(cassandraCluster.getCassandraCluster().getConf(),
            new HashBlobId.Factory()), new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.of("default"), new HashBlobId.Factory()),
            clock);
        uploadUsageRepository = new CassandraUploadUsageRepository(new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()));
        jmapCurrentUploadUsageCalculator = new JMAPCurrentUploadUsageCalculator(uploadRepository, uploadUsageRepository);
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
    public JMAPCurrentUploadUsageCalculator currentUploadUsageRecomputator() {
        return jmapCurrentUploadUsageCalculator;
    }
}
