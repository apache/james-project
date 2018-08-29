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

import java.net.URI;
import java.util.Properties;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.reference.TempAuthHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerSwiftTempAuthExtension.class)
public class ObjectStorageBlobsDAOTest implements BlobStoreContract {
    private static final String TENANT_NAME = "test";
    private static final String USER_NAME = "tester";
    private static final Credentials PASSWORD = Credentials.of("testing");
    private static final Identity IDENTITY = Identity.of(TENANT_NAME + ":" + USER_NAME);

    private URI swiftEndpoint;
    private ContainerName containerName;
    private org.jclouds.blobstore.BlobStore blobStore;

    @BeforeEach
    void setUp(DockerSwiftTempAuthExtension.DockerSwift dockerSwift) throws Exception {
        final Properties overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, "tempAuthCredentials");
        overrides.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_USER, "X-Storage-User");
        overrides.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_PASS, "X-Storage-Pass");
        swiftEndpoint = dockerSwift.getEndpoint();
        BlobStoreContext blobStoreContext = ContextBuilder.newBuilder("openstack-swift")
            .endpoint(swiftEndpoint.toString())
            .credentials(IDENTITY.value(), PASSWORD.value())
            .overrides(overrides)
            .buildView(BlobStoreContext.class);
        blobStore = blobStoreContext.getBlobStore();
        containerName = ContainerName.of(UUID.randomUUID().toString());
        blobStore.createContainerInLocation(null, containerName.value());
    }

    @AfterEach
    void tearDown() throws Exception {
        blobStore.deleteContainer(containerName.value());
        blobStore.getContext().close();
    }

    @Override
    public BlobStore testee() {
        ObjectStorageConfiguration testConfig =
            new ObjectStorageConfiguration.Builder()
                .endpoint(swiftEndpoint)
                .identity(IDENTITY)
                .credentials(PASSWORD)
                .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
                .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
                .build();
        return new ObjectStorageBlobsDAO(containerName, new HashBlobId.Factory(), testConfig);
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }
}

