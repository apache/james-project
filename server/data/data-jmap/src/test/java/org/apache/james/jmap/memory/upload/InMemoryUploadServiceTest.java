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

package org.apache.james.jmap.memory.upload;

import java.time.Clock;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.api.upload.UploadService;
import org.apache.james.jmap.api.upload.UploadServiceContract;
import org.apache.james.jmap.api.upload.UploadServiceDefaultImpl;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;

public class InMemoryUploadServiceTest implements UploadServiceContract {

    private UploadRepository uploadRepository;
    private UploadUsageRepository uploadUsageRepository;
    private UploadService testee;

    @BeforeEach
    void setUp() {
        BlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, new HashBlobId.Factory());
        uploadRepository = new InMemoryUploadRepository(blobStore, Clock.systemUTC());
        uploadUsageRepository = new InMemoryUploadUsageRepository();
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
