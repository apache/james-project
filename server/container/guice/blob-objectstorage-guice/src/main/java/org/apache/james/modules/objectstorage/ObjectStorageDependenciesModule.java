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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.PutBlobFunction;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.AwsS3ObjectStorage;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.modules.objectstorage.swift.SwiftObjectStorage;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class ObjectStorageDependenciesModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(BlobId.Factory.class).to(HashBlobId.Factory.class).in(Scopes.SINGLETON);
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
    private ObjectStorageBlobsDAO buildObjectStore(ObjectStorageBlobConfiguration configuration, BlobId.Factory blobIdFactory, Provider<AwsS3ObjectStorage> awsS3ObjectStorageProvider) throws InterruptedException, ExecutionException, TimeoutException {
        ObjectStorageBlobsDAO dao = selectDaoBuilder(configuration)
            .container(configuration.getNamespace())
            .blobIdFactory(blobIdFactory)
            .payloadCodec(configuration.getPayloadCodec())
            .putBlob(putBlob(blobIdFactory, configuration, awsS3ObjectStorageProvider))
            .build();
        dao.createContainer(configuration.getNamespace()).block(Duration.ofMinutes(1));
        return dao;
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName selectDaoBuilder(ObjectStorageBlobConfiguration configuration) {
        switch (configuration.getProvider()) {
            case SWIFT:
                return SwiftObjectStorage.builder(configuration);
            case AWSS3:
                return AwsS3ObjectStorage.daoBuilder((AwsS3AuthConfiguration) configuration.getSpecificAuthConfiguration());
        }
        throw new IllegalArgumentException("unknown provider " + configuration.getProvider());
    }

    private Optional<PutBlobFunction> putBlob(BlobId.Factory blobIdFactory, ObjectStorageBlobConfiguration configuration, Provider<AwsS3ObjectStorage> awsS3ObjectStorageProvider) {
        switch (configuration.getProvider()) {
            case SWIFT:
                return Optional.empty();
            case AWSS3:
                return awsS3ObjectStorageProvider
                    .get()
                    .putBlob(configuration.getNamespace(), (AwsS3AuthConfiguration) configuration.getSpecificAuthConfiguration());
        }
        throw new IllegalArgumentException("unknown provider " + configuration.getProvider());

    }

}
