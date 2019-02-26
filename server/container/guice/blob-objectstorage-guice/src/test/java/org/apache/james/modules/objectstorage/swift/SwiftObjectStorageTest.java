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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.ObjectStorageProvider;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.junit.jupiter.api.Test;

class SwiftObjectStorageTest {

    @Test
    void builderShouldThrowWhenTempAuthAPIAndNoConfiguration() throws Exception {
        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(new SwiftAuthConfiguration("tmpauth", Optional.empty(), Optional.empty(), Optional.empty()))
            .build();
        assertThatThrownBy(() -> SwiftObjectStorage.builder(objectStorageBlobConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No TempAuth configuration found for tmpauth API");
    }

    @Test
    void builderShouldWorkWhenTempAuthAPI() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstorage.swift.endpoint", "http://auth.example.com/v2.0");
        configuration.addProperty("objectstorage.swift.credentials", "this_is_a_secret");
        configuration.addProperty("objectstorage.swift.tempauth.username", "user");
        configuration.addProperty("objectstorage.swift.tempauth.tenantname", "tenant");
        configuration.addProperty("objectstorage.swift.authapi", "tmpauth");

        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(SwiftAuthConfiguration.from(configuration))
            .build();
        SwiftObjectStorage.builder(objectStorageBlobConfiguration);
    }

    @Test
    void builderShouldThrowWhenKeystone2APIAndNoConfiguration() throws Exception {
        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(new SwiftAuthConfiguration("keystone2", Optional.empty(), Optional.empty(), Optional.empty()))
            .build();
        assertThatThrownBy(() -> SwiftObjectStorage.builder(objectStorageBlobConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No Keystone2 configuration found for keystone2 API");
    }

    @Test
    void builderShouldWorkWhenKeystone2API() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstorage.swift.endpoint", "http://auth.example.com/v2.0");
        configuration.addProperty("objectstorage.swift.credentials", "this_is_a_secret");
        configuration.addProperty("objectstorage.swift.keystone2.username", "user");
        configuration.addProperty("objectstorage.swift.keystone2.tenantname", "tenant");
        configuration.addProperty("objectstorage.swift.authapi", "keystone2");

        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(SwiftAuthConfiguration.from(configuration))
            .build();
        SwiftObjectStorage.builder(objectStorageBlobConfiguration);
    }

    @Test
    void builderShouldThrowWhenKeystone3APIAndNoConfiguration() throws Exception {
        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(new SwiftAuthConfiguration("keystone3", Optional.empty(), Optional.empty(), Optional.empty()))
            .build();
        assertThatThrownBy(() -> SwiftObjectStorage.builder(objectStorageBlobConfiguration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No Keystone3 configuration found for keystone3 API");
    }

    @Test
    void builderShouldWorkWhenKeystone3API() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("objectstorage.swift.endpoint", "http://auth.example.com/v2.0");
        configuration.addProperty("objectstorage.swift.credentials", "this_is_a_secret");
        configuration.addProperty("objectstorage.swift.keystone3.user.name", "user");
        configuration.addProperty("objectstorage.swift.keystone3.user.domain", "domain");
        configuration.addProperty("objectstorage.swift.authapi", "keystone3");

        ObjectStorageBlobConfiguration objectStorageBlobConfiguration = ObjectStorageBlobConfiguration.builder()
            .codec(PayloadCodecFactory.DEFAULT)
            .provider(ObjectStorageProvider.SWIFT)
            .container(ContainerName.of("myContainer"))
            .authConfiguration(SwiftAuthConfiguration.from(configuration))
            .build();
        SwiftObjectStorage.builder(objectStorageBlobConfiguration);
    }
}