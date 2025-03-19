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

package org.apache.james.blob.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAOContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraBlobStoreDAOTest implements BlobStoreDAOContract {
    private static final int CHUNK_SIZE = 10240;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobDataDefinition.MODULE);

    private BlobStoreDAO testee;
    private CassandraDefaultBucketDAO defaultBucketDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        TestBlobId.Factory blobIdFactory = new TestBlobId.Factory();
        CassandraBucketDAO bucketDAO = new CassandraBucketDAO(blobIdFactory, cassandra.getConf());
        defaultBucketDAO = new CassandraDefaultBucketDAO(cassandra.getConf(), blobIdFactory);
        testee = new CassandraBlobStoreDAO(
            defaultBucketDAO,
            bucketDAO,
            CassandraConfiguration.builder()
                .blobPartSize(CHUNK_SIZE)
                .build(),
            BucketName.DEFAULT,
            new RecordingMetricFactory());
    }

    @Override
    public BlobStoreDAO testee() {
        return testee;
    }

    @Override
    @Disabled("Not supported by the Cassandra blob store")
    public void listBucketsShouldReturnBucketsWithNoBlob() {

    }
}