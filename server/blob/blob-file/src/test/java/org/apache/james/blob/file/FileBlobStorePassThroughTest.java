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

package org.apache.james.blob.file;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.DeleteBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;

public class FileBlobStorePassThroughTest implements DeleteBlobStoreContract, MetricableBlobStoreContract {

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private BlobStore blobStore;

    @BeforeEach
    void setUp() {
        FileSystemImpl fileSystem = FileSystemImpl.forTesting();
        blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), new FileBlobStoreFactory(fileSystem).builder()
                .blobIdFactory(BLOB_ID_FACTORY)
                .defaultBucketName()
                .passthrough());
    }

    @Override
    public BlobStore testee() {
        return blobStore;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return BLOB_ID_FACTORY;
    }
}
