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

package org.apache.james.modules.objectstorage;

import static org.apache.james.modules.objectstorage.ObjectStorageBlobsDAOProvider.OBJECTSTORAGE_CONFIGURATION_NAME;
import static org.apache.james.modules.objectstorage.ObjectStorageBlobsDAOProvider.OBJECTSTORAGE_NAMESPACE;
import static org.apache.james.modules.objectstorage.ObjectStorageBlobsDAOProvider.OBJECTSTORAGE_PROVIDER;
import static org.apache.james.modules.objectstorage.ObjectStorageBlobsDAOProvider.OBJECTSTORAGE_PROVIDER_SWIFT;
import static org.apache.james.modules.objectstorage.ObjectStorageBlobsDAOProvider.OBJECTSTORAGE_SWIFT_AUTH_API;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.DockerSwift;
import org.apache.james.blob.objectstorage.DockerSwiftExtension;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerSwiftExtension.class)
class ObjectStorageBlobsDAOProviderTest {

    private ContainerName containerName;
    private DockerSwift dockerSwift;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) throws Exception {
        this.dockerSwift = dockerSwift;
        containerName = ContainerName.of(UUID.randomUUID().toString());
    }

    public static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();


    private PropertiesProvider tempAuthPropertiesProvider() {
        return FakePropertiesProvider.builder()
            .register(OBJECTSTORAGE_CONFIGURATION_NAME,
                swiftConfigBuilder()
                    .put(OBJECTSTORAGE_SWIFT_AUTH_API, SwiftTempAuthObjectStorage.AUTH_API_NAME)
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_ENDPOINT, dockerSwift.swiftEndpoint().toString())
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_CREDENTIALS, "testing")
                    .put(SwiftTmpAuthConfigurationReader.OBJECTSTORAGE_SWIFT_TEMPAUTH_USERNAME, "tester")
                    .put(SwiftTmpAuthConfigurationReader.OBJECTSTORAGE_SWIFT_TEMPAUTH_TENANTNAME, "test")
                    .put(SwiftTmpAuthConfigurationReader.OBJECTSTORAGE_SWIFT_TEMPAUTH_PASS_HEADER_NAME, "X-Storage-Pass")
                    .put(SwiftTmpAuthConfigurationReader.OBJECTSTORAGE_SWIFT_TEMPAUTH_USER_HEADER_NAME, "X-Storage-User")
                    .build())
            .build();
    }

    private PropertiesProvider keystone2PropertiesProvider() {
        return FakePropertiesProvider.builder()
            .register(OBJECTSTORAGE_CONFIGURATION_NAME,
                swiftConfigBuilder()
                    .put(OBJECTSTORAGE_SWIFT_AUTH_API, SwiftKeystone2ObjectStorage.AUTH_API_NAME)
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_ENDPOINT, dockerSwift.keystoneV2Endpoint().toString())
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_CREDENTIALS, "demo")
                    .put(SwiftKeystone2ConfigurationReader.OBJECTSTORAGE_SWIFT_KEYSTONE_2_USERNAME, "demo")
                    .put(SwiftKeystone2ConfigurationReader.OBJECTSTORAGE_SWIFT_KEYSTONE_2_TENANTNAME, "test")
                    .build())
            .build();
    }

    private final PropertiesProvider keystone3PropertiesProvider() {
        return FakePropertiesProvider.builder()
            .register(OBJECTSTORAGE_CONFIGURATION_NAME,
                swiftConfigBuilder()
                    .put(OBJECTSTORAGE_SWIFT_AUTH_API, SwiftKeystone3ObjectStorage.AUTH_API_NAME)
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_ENDPOINT, dockerSwift.keystoneV3Endpoint().toString())
                    .put(SwiftConfigurationReader.OBJECTSTORAGE_SWIFT_CREDENTIALS, "demo")
                    .put(SwiftKeystone3ConfigurationReader.OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_NAME, "demo")
                    .put(SwiftKeystone3ConfigurationReader.OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_DOMAIN, "Default")
                    .put(SwiftKeystone3ConfigurationReader.OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_NAME, "test")
                    .build())
            .build();
    }

    @Test
    void providesTempauthBackedBlobstoreDao() throws ConfigurationException {
        ObjectStorageBlobsDAOProvider objectStorageBlobsDAOProvider =
            new ObjectStorageBlobsDAOProvider(tempAuthPropertiesProvider(), BLOB_ID_FACTORY);
        ObjectStorageBlobsDAO objectStorageBlobsDAO = objectStorageBlobsDAOProvider.get();
        assertThat(objectStorageBlobsDAO).isNotNull();
    }

    @Test
    void providesKeystone2BackedBlobstoreDao() throws ConfigurationException {
        ObjectStorageBlobsDAOProvider objectStorageBlobsDAOProvider =
            new ObjectStorageBlobsDAOProvider(keystone2PropertiesProvider(),
                BLOB_ID_FACTORY);
        ObjectStorageBlobsDAO objectStorageBlobsDAO = objectStorageBlobsDAOProvider.get();
        assertThat(objectStorageBlobsDAO).isNotNull();
    }

    @Test
    void providesKeystone3BackedBlobstoreDao() throws ConfigurationException {
        ObjectStorageBlobsDAOProvider objectStorageBlobsDAOProvider =
            new ObjectStorageBlobsDAOProvider(keystone3PropertiesProvider(),
                BLOB_ID_FACTORY);
        ObjectStorageBlobsDAO objectStorageBlobsDAO = objectStorageBlobsDAOProvider.get();
        assertThat(objectStorageBlobsDAO).isNotNull();
    }

    private static MapConfigurationBuilder swiftConfigBuilder() {
        return newConfigBuilder()
            .put(PayloadCodecProvider.OBJECTSTORAGE_PAYLOAD_CODEC, PayloadCodecs.DEFAULT.name())
            .put(OBJECTSTORAGE_PROVIDER, OBJECTSTORAGE_PROVIDER_SWIFT)
            .put(OBJECTSTORAGE_NAMESPACE, "foo");
    }

    private static MapConfigurationBuilder newConfigBuilder() {
        return new MapConfigurationBuilder();
    }
}
