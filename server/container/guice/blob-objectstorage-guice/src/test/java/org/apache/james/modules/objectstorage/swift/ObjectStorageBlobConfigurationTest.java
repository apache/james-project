/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.api.BucketName;
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
import org.apache.james.modules.objectstorage.MapConfigurationBuilder;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.ObjectStorageProvider;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import com.google.common.collect.ImmutableMap;

class ObjectStorageBlobConfigurationTest {

    static final ImmutableMap<String, Object> CONFIGURATION_WITHOUT_CODEC = ImmutableMap.<String, Object>builder()
        .put("objectstorage.provider", "swift")
        .put("objectstorage.namespace", "foo")
        .put("objectstorage.swift.authapi", "tmpauth")
        .put("objectstorage.swift.endpoint", "http://swift/endpoint")
        .put("objectstorage.swift.credentials", "testing")
        .put("objectstorage.swift.tempauth.username", "tester")
        .put("objectstorage.swift.tempauth.tenantname", "test")
        .put("objectstorage.swift.tempauth.passheadername", "X-Storage-Pass")
        .put("objectstorage.swift.tempauth.userheadername", "X-Storage-User")
        .build();

    static final ImmutableMap<String, Object> VALID_CONFIGURATION = ImmutableMap.<String, Object>builder()
        .putAll(CONFIGURATION_WITHOUT_CODEC)
        .put("objectstorage.payload.codec", "DEFAULT")
        .build();

    static class RequiredParameters implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of("objectstorage.provider", "objectstorage.namespace", "objectstorage.swift.authapi", "objectstorage.payload.codec")
                    .map(Arguments::of);
        }
    }

    @Test
    void tempAuthPropertiesProvider() throws ConfigurationException {
        ObjectStorageBlobConfiguration configuration = ObjectStorageBlobConfiguration.from(
            new MapConfigurationBuilder()
                .put("objectstorage.payload.codec", PayloadCodecFactory.DEFAULT.name())
                .put("objectstorage.provider", "swift")
                .put("objectstorage.namespace", "foo")
                .put("objectstorage.swift.authapi", "tmpauth")
                .put("objectstorage.swift.endpoint", "http://swift/endpoint")
                .put("objectstorage.swift.credentials", "testing")
                .put("objectstorage.swift.tempauth.username", "tester")
                .put("objectstorage.swift.tempauth.tenantname", "test")
                .put("objectstorage.swift.tempauth.passheadername", "X-Storage-Pass")
                .put("objectstorage.swift.tempauth.userheadername", "X-Storage-User")
                .build());
        assertThat(configuration)
            .isEqualTo(
                ObjectStorageBlobConfiguration.builder()
                    .codec(PayloadCodecFactory.DEFAULT)
                    .provider(ObjectStorageProvider.SWIFT)
                    .authConfiguration(new SwiftAuthConfiguration(SwiftTempAuthObjectStorage.AUTH_API_NAME,
                        Optional.of(SwiftTempAuthObjectStorage.configBuilder()
                            .endpoint(URI.create("http://swift/endpoint"))
                            .credentials(Credentials.of("testing"))
                            .userName(UserName.of("tester"))
                            .tenantName(TenantName.of("test"))
                            .tempAuthHeaderUserName(UserHeaderName.of("X-Storage-User"))
                            .tempAuthHeaderPassName(PassHeaderName.of("X-Storage-Pass"))
                            .build()),
                        Optional.empty(),
                        Optional.empty()))
                    .defaultBucketName(BucketName.of("foo"))
                    .build());
    }

    @Test
    void keystone2PropertiesProvider() throws ConfigurationException {
        ObjectStorageBlobConfiguration configuration = ObjectStorageBlobConfiguration.from(
            new MapConfigurationBuilder()
                .put("objectstorage.payload.codec", PayloadCodecFactory.DEFAULT.name())
                .put("objectstorage.provider", "swift")
                .put("objectstorage.namespace", "foo")
                .put("objectstorage.swift.authapi", "keystone2")
                .put("objectstorage.swift.endpoint", "http://swift/endpoint")
                .put("objectstorage.swift.credentials", "creds")
                .put("objectstorage.swift.keystone2.username", "demo")
                .put("objectstorage.swift.keystone2.tenantname", "test")
                .build());
        assertThat(configuration)
            .isEqualTo(
                ObjectStorageBlobConfiguration.builder()
                    .codec(PayloadCodecFactory.DEFAULT)
                    .provider(ObjectStorageProvider.SWIFT)
                    .authConfiguration(new SwiftAuthConfiguration(SwiftKeystone2ObjectStorage.AUTH_API_NAME,
                        Optional.empty(),
                        Optional.of(SwiftKeystone2ObjectStorage.configBuilder()
                            .endpoint(URI.create("http://swift/endpoint"))
                            .credentials(Credentials.of("creds"))
                            .userName(UserName.of("demo"))
                            .tenantName(TenantName.of("test"))
                            .build()),
                        Optional.empty()))
                    .defaultBucketName(BucketName.of("foo"))
                    .build());
    }

    @Test
    void keystone3PropertiesProvider() throws ConfigurationException {
        ObjectStorageBlobConfiguration configuration = ObjectStorageBlobConfiguration.from(
            new MapConfigurationBuilder()
                .put("objectstorage.payload.codec", PayloadCodecFactory.DEFAULT.name())
                .put("objectstorage.provider", "swift")
                .put("objectstorage.namespace", "foo")
                .put("objectstorage.swift.authapi", "keystone3")
                .put("objectstorage.swift.endpoint", "http://swift/endpoint")
                .put("objectstorage.swift.credentials", "creds")
                .put("objectstorage.swift.keystone3.user.name", "demo")
                .put("objectstorage.swift.keystone3.user.domain", "Default")
                .put("objectstorage.swift.keystone3.scope.project.name", "test")
                .build());
        assertThat(configuration)
            .isEqualTo(
                ObjectStorageBlobConfiguration.builder()
                    .codec(PayloadCodecFactory.DEFAULT)
                    .provider(ObjectStorageProvider.SWIFT)
                    .authConfiguration(new SwiftAuthConfiguration(SwiftKeystone3ObjectStorage.AUTH_API_NAME,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(SwiftKeystone3ObjectStorage.configBuilder()
                            .endpoint(URI.create("http://swift/endpoint"))
                            .credentials(Credentials.of("creds"))
                            .project(Project.of(ProjectName.of("test")))
                            .identity(IdentityV3.of(DomainName.of("Default"), UserName.of("demo")))
                            .build())))
                    .defaultBucketName(BucketName.of("foo"))
                    .build());
    }

}