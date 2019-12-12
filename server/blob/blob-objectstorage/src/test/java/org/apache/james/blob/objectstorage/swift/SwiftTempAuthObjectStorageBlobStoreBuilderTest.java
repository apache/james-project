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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.DockerSwift;
import org.apache.james.blob.objectstorage.DockerSwiftExtension;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerSwiftExtension.class)
class SwiftTempAuthObjectStorageBlobStoreBuilderTest implements ObjectStorageBlobStoreContract {

    private static final TenantName TENANT_NAME = TenantName.of("test");
    private static final UserName USER_NAME = UserName.of("tester");
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity SWIFT_IDENTITY = Identity.of(TENANT_NAME, USER_NAME);
    private BucketName defaultBucketName;
    private URI endpoint;
    private SwiftTempAuthObjectStorage.Configuration testConfig;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) {
        defaultBucketName = BucketName.of("ea21582e-f6c1-4737-867a-b25d0b36eccc");
        endpoint = dockerSwift.swiftEndpoint();
        testConfig = SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(endpoint)
            .identity(SWIFT_IDENTITY)
            .credentials(PASSWORD)
            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
            .build();
    }

    @Override
    public BucketName defaultBucketName() {
        return defaultBucketName;
    }

    @Test
    void blobIdFactoryIsMandatoryToBuildBlobStore() {
        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(testConfig)
            .blobIdFactory(null)
            .namespace(defaultBucketName);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builtBlobStoreCanStoreAndRetrieve() {
        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(testConfig)
            .blobIdFactory(new HashBlobId.Factory())
            .namespace(defaultBucketName);

        assertBlobStoreCanStoreAndRetrieve(builder);
    }
}