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

import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.ObjectStorageProvider;

public class SwiftObjectStorage {

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(ObjectStorageBlobConfiguration configuration) {
        if (configuration.getProvider() != ObjectStorageProvider.SWIFT) {
            throw new IllegalArgumentException("unknown provider " + configuration.getProvider());
        }
        SwiftAuthConfiguration authConfiguration = (SwiftAuthConfiguration) configuration.getSpecificAuthConfiguration();
        switch (authConfiguration.getAuthApiName()) {
            case SwiftTempAuthObjectStorage.AUTH_API_NAME:
                return authConfiguration.getTempAuth()
                                    .map(ObjectStorageBlobStore::builder)
                                    .orElseThrow(() -> new IllegalArgumentException("No TempAuth configuration found for tmpauth API"));
            case SwiftKeystone2ObjectStorage.AUTH_API_NAME:
                return authConfiguration.getKeystone2Configuration()
                                    .map(ObjectStorageBlobStore::builder)
                                    .orElseThrow(() -> new IllegalArgumentException("No Keystone2 configuration found for keystone2 API"));
            case SwiftKeystone3ObjectStorage.AUTH_API_NAME:
                return authConfiguration.getKeystone3Configuration()
                                    .map(ObjectStorageBlobStore::builder)
                                    .orElseThrow(() -> new IllegalArgumentException("No Keystone3 configuration found for keystone3 API"));
            default:
                throw new IllegalArgumentException("unknown auth api " + authConfiguration.getAuthApiName());
        }
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftTempAuthObjectStorage.Configuration testConfig) {
        return SwiftTempAuthObjectStorage.blobStoreBuilder(testConfig);
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftKeystone2ObjectStorage.Configuration testConfig) {
        return SwiftKeystone2ObjectStorage.blobStoreBuilder(testConfig);
    }

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory builder(SwiftKeystone3ObjectStorage.Configuration testConfig) {
        return SwiftKeystone3ObjectStorage.blobStoreBuilder(testConfig);
    }
}
