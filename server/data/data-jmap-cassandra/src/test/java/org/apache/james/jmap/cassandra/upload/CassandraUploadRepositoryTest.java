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
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadRepositoryContract;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

class CassandraUploadRepositoryTest implements UploadRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(UploadModule.MODULE);
    private CassandraUploadRepository testee;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        testee = new CassandraUploadRepository(new UploadDAO(cassandra.getCassandraCluster().getConf(),
            new HashBlobId.Factory()), new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.of("default"), new HashBlobId.Factory()),
            clock);
    }

    @Override
    public UploadId randomUploadId() {
        return UploadId.from(Uuids.timeBased());
    }

    @Override
    public UploadRepository testee() {
        return testee;
    }

}