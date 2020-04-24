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

import java.io.FileNotFoundException;
import java.util.Optional;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.objectstorage.BlobPutter;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.modules.objectstorage.swift.SwiftObjectStorage;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ObjectStorageDependenciesModule extends AbstractModule {

    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    private ObjectStorageBlobConfiguration getObjectStorageConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return ObjectStorageBlobConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException(ConfigurationComponent.NAME + " configuration was not found");
        }
    }

    @Provides
    @Singleton
    private ObjectStorageBlobStore buildObjectStore(ObjectStorageBlobConfiguration configuration, BlobId.Factory blobIdFactory, Provider<AwsS3ObjectStorage> awsS3ObjectStorageProvider) {
        ObjectStorageBlobStore blobStore = selectBlobStoreBuilder(configuration)
            .blobIdFactory(blobIdFactory)
            .payloadCodec(configuration.getPayloadCodec())
            .blobPutter(putBlob(configuration, awsS3ObjectStorageProvider))
            .namespace(configuration.getNamespace())
            .bucketPrefix(configuration.getBucketPrefix())
            .build();
        return blobStore;
    }

    private ObjectStorageBlobStoreBuilder.RequireBlobIdFactory selectBlobStoreBuilder(ObjectStorageBlobConfiguration configuration) {
        switch (configuration.getProvider()) {
            case SWIFT:
                return SwiftObjectStorage.builder(configuration);
            case AWSS3:
                return AwsS3ObjectStorage.blobStoreBuilder((AwsS3AuthConfiguration) configuration.getSpecificAuthConfiguration());
        }
        throw new IllegalArgumentException("unknown provider " + configuration.getProvider());
    }

    private Optional<BlobPutter> putBlob(ObjectStorageBlobConfiguration configuration, Provider<AwsS3ObjectStorage> awsS3ObjectStorageProvider) {
        switch (configuration.getProvider()) {
            case SWIFT:
                return Optional.empty();
            case AWSS3:
                return awsS3ObjectStorageProvider
                    .get()
                    .putBlob((AwsS3AuthConfiguration) configuration.getSpecificAuthConfiguration());
        }
        throw new IllegalArgumentException("unknown provider " + configuration.getProvider());

    }

}
