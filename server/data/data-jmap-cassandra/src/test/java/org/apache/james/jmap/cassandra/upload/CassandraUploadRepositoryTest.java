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
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadRepositoryContract;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

class CassandraUploadRepositoryTest implements UploadRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(UploadDataDefinition.MODULE);
    private BlobStoreDAO blobStoreDAO;
    private CassandraUploadRepository testee;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() {
        clock = new UpdatableTickingClock(Clock.systemUTC().instant());
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        blobStoreDAO = new MemoryBlobStoreDAO();
        testee = new CassandraUploadRepository(new UploadDAO(cassandra.getCassandraCluster().getConf(),
            blobIdFactory), blobIdFactory, blobStoreDAO, clock);
    }

    @Override
    public UploadId randomUploadId() {
        return UploadId.from(Uuids.timeBased());
    }

    @Override
    public UploadRepository testee() {
        return testee;
    }


    @Disabled("Delete method always return true (to avoid LWT)")
    @Override
    public void deleteShouldReturnTrueWhenRowExists() {
        UploadRepositoryContract.super.deleteShouldReturnTrueWhenRowExists();
    }

    @Disabled("Delete method always return true (to avoid LWT)")
    @Override
    public void deleteShouldReturnFalseWhenRowDoesNotExist() {
        UploadRepositoryContract.super.deleteShouldReturnFalseWhenRowDoesNotExist();
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }

    @Override
    public BlobStoreDAO blobStoreDAO() {
        return blobStoreDAO;
    }
}