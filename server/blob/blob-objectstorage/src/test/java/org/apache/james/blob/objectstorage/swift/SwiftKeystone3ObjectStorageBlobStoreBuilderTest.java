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

import java.util.HashMap;
import java.util.Map;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(DockerSwiftExtension.class)
class SwiftKeystone3ObjectStorageBlobStoreBuilderTest implements ObjectStorageBlobStoreContract {

    private static final DomainName DOMAIN_NAME = DomainName.of("Default");
    private static final DomainId DOMAIN_ID = DomainId.of("default");
    private static final ProjectName PROJECT_NAME = ProjectName.of("test");
    private static final UserName DEMO_USER_NAME = UserName.of("demo");
    private static final Credentials DEMO_PASSWORD = Credentials.of("demo");
    private static final IdentityV3 DEMO_IDENTITY = IdentityV3.of(DOMAIN_NAME, DEMO_USER_NAME);

    private static final String PROJECT_CONFIG_KEY = "PROJECT_CONFIG_KEY";
    private static final SwiftKeystone3ObjectStorage.Configuration.Builder PROJECT_CONFIG =
         SwiftKeystone3ObjectStorage.configBuilder()
            .identity(DEMO_IDENTITY)
            .credentials(DEMO_PASSWORD)
            .project(Project.of(PROJECT_NAME));

    private static final String PROJECT_DOMAIN_NAME_KEY = "PROJECT_DOMAIN_NAME_KEY";
    private static final SwiftKeystone3ObjectStorage.Configuration.Builder PROJECT_DOMAIN_NAME_SCOPE =
        SwiftKeystone3ObjectStorage.configBuilder()
            .identity(DEMO_IDENTITY)
            .credentials(DEMO_PASSWORD)
            .project(Project.of(PROJECT_NAME, DOMAIN_NAME));

    private static final String PROJECT_DOMAIN_ID_KEY = "PROJECT_DOMAIN_ID_KEY";
    private static final SwiftKeystone3ObjectStorage.Configuration.Builder PROJECT_DOMAIN_ID_SCOPE =
        SwiftKeystone3ObjectStorage.configBuilder()
            .identity(DEMO_IDENTITY)
            .credentials(DEMO_PASSWORD)
            .project(Project.of(PROJECT_NAME, DOMAIN_ID));

    private BucketName defaultBucketName;

    private SwiftKeystone3ObjectStorage.Configuration testConfig;
    private DockerSwift dockerSwift;
    private Map<String, SwiftKeystone3ObjectStorage.Configuration.Builder> configBuilders;

    @BeforeEach
    void setUp(DockerSwift dockerSwift) {
        this.dockerSwift = dockerSwift;
        defaultBucketName = BucketName.of("4f75f792-205c-4279-a2ab-dbf2fba1c24d");
        testConfig = PROJECT_CONFIG
            .endpoint(dockerSwift.keystoneV3Endpoint())
            .build();
        configBuilders = new HashMap<>();
        // There should be 2 more modes: unscoped and domain-scoped
        // but the docker image doesn't support them...
        configBuilders.put(PROJECT_CONFIG_KEY, PROJECT_CONFIG);
        configBuilders.put(PROJECT_DOMAIN_ID_KEY, PROJECT_DOMAIN_ID_SCOPE);
        configBuilders.put(PROJECT_DOMAIN_NAME_KEY, PROJECT_DOMAIN_NAME_SCOPE);
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

    @ParameterizedTest
    @ValueSource(strings = {PROJECT_CONFIG_KEY, PROJECT_DOMAIN_ID_KEY, PROJECT_DOMAIN_NAME_KEY})
    void builtBlobStoreCanStoreAndRetrieve(String key) {
        SwiftKeystone3ObjectStorage.Configuration config =
            configBuilders.get(key).endpoint(dockerSwift.keystoneV3Endpoint()).build();
        ObjectStorageBlobStoreBuilder.ReadyToBuild builder = ObjectStorageBlobStore
            .builder(config)
            .blobIdFactory(new HashBlobId.Factory())
            .namespace(defaultBucketName);

        assertBlobStoreCanStoreAndRetrieve(builder);
    }
}