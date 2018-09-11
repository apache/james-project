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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.Identity;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerSwiftExtension.class)
public class ObjectStorageBlobsDAOTest implements BlobStoreContract {
    private static final TenantName TENANT_NAME = TenantName.of("test");
    private static final UserName USER_NAME = UserName.of("tester");
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity SWIFT_IDENTITY = Identity.of(TENANT_NAME, USER_NAME);

    private ContainerName containerName;
    private org.jclouds.blobstore.BlobStore blobStore;
    private SwiftTempAuthObjectStorage.Configuration testConfig;
    private ObjectStorageBlobsDAO testee;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) throws Exception {
        containerName = ContainerName.of(UUID.randomUUID().toString());
        testConfig = SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(dockerSwift.swiftEndpoint())
            .identity(SWIFT_IDENTITY)
            .credentials(PASSWORD)
            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
            .build();
        BlobId.Factory blobIdFactory = blobIdFactory();
        blobStore = ObjectStorageBlobsDAO
            .builder(testConfig)
            .container(containerName)
            .blobIdFactory(blobIdFactory)
            .getSupplier().get();
        testee = new ObjectStorageBlobsDAO(containerName, blobIdFactory, blobStore);
        testee.createContainer(containerName);
    }

    @AfterEach
    void tearDown() throws Exception {
        blobStore.deleteContainer(containerName.value());
        blobStore.getContext().close();
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Test
    void canCreateContainer() throws Exception {
        ContainerName containerName = ContainerName.of(UUID.randomUUID().toString());
        testee.createContainer(containerName).get();
        assertThat(blobStore.containerExists(containerName.value())).isTrue();
    }
    @Test
    void failsWithRuntimeExceptionOnCreateContainerTwice() throws Exception {
        ContainerName containerName = ContainerName.of(UUID.randomUUID().toString());
        testee.createContainer(containerName).get();
        assertThatThrownBy(() -> testee.createContainer(containerName).get())
            .hasCauseInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("Unable to create container");
    }
}

