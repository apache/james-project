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

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.DockerSwift;
import org.apache.james.blob.objectstorage.DockerSwiftExtension;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.DomainName;
import org.apache.james.blob.objectstorage.swift.IdentityV3;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.Project;
import org.apache.james.blob.objectstorage.swift.ProjectName;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;

@ExtendWith(DockerSwiftExtension.class)
class ObjectStorageBlobStoreModuleTest {

    private static DockerSwift dockerSwift;

    @BeforeAll
    static void setUp(DockerSwift dockerSwift) throws Exception {
        ObjectStorageBlobStoreModuleTest.dockerSwift = dockerSwift;
    }

    static class BlobStorageBlobConfigurationProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {

            ObjectStorageBlobConfiguration tmpAuth = ObjectStorageBlobConfiguration.builder()
                .codec(PayloadCodecFactory.DEFAULT)
                .swift()
                .container(generateContainerName())
                .tempAuth(SwiftTempAuthObjectStorage.configBuilder()
                    .endpoint(dockerSwift.swiftEndpoint())
                    .credentials(Credentials.of("testing"))
                    .userName(UserName.of("tester"))
                    .tenantName(TenantName.of("test"))
                    .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
                    .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
                    .build())
                .build();
            ObjectStorageBlobConfiguration keystone2 = ObjectStorageBlobConfiguration.builder()
                .codec(PayloadCodecFactory.DEFAULT)
                .swift()
                .container(generateContainerName())
                .keystone2(SwiftKeystone2ObjectStorage.configBuilder()
                    .endpoint(dockerSwift.keystoneV2Endpoint())
                    .credentials(Credentials.of("demo"))
                    .userName(UserName.of("demo"))
                    .tenantName(TenantName.of("test"))
                    .build())
                .build();
            ObjectStorageBlobConfiguration keystone3 = ObjectStorageBlobConfiguration.builder()
                .codec(PayloadCodecFactory.DEFAULT)
                .swift()
                .container(generateContainerName())
                .keystone3(SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(dockerSwift.keystoneV3Endpoint())
                    .credentials(Credentials.of("demo"))
                    .project(Project.of(ProjectName.of("test")))
                    .identity(IdentityV3.of(DomainName.of("Default"), UserName.of("demo")))
                    .build())
                .build();
            return Stream.of(tmpAuth, keystone2, keystone3).map(Arguments::of);
        }
    }

    private static ContainerName generateContainerName() {
        return ContainerName.of(UUID.randomUUID().toString());
    }

    @ParameterizedTest
    @ArgumentsSource(BlobStorageBlobConfigurationProvider.class)
    void shouldSetupBlobStore(ObjectStorageBlobConfiguration configuration) {
        Injector injector = Guice.createInjector(
            Modules
                .override(new ObjectStorageBlobStoreModule())
                .with(binder -> binder.bind(ObjectStorageBlobConfiguration.class).toInstance(configuration)));

        ObjectStorageBlobsDAO dao = injector.getInstance(ObjectStorageBlobsDAO.class);
        dao.createContainer(configuration.getNamespace());

        BlobStore blobStore = injector.getInstance(BlobStore.class);

        assertThatCode(() -> blobStore.save(new byte[] {0x00})).doesNotThrowAnyException();
    }

}
