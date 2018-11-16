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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class ObjectStorageDependenciesModule extends AbstractModule {

    private static final String OBJECTSTORAGE_PROVIDER_SWIFT = "swift";

    @Override
    protected void configure() {
        bind(BlobId.Factory.class).to(HashBlobId.Factory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    private PayloadCodec buildPayloadCodec(ObjectStorageBlobConfiguration configuration) {
        return configuration.getPayloadCodecFactory().create(configuration);
    }

    @Provides
    @Singleton
    private ObjectStorageBlobConfiguration getObjectStorageConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ConfigurationComponent.NAME);
            return ObjectStorageBlobConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException(ConfigurationComponent.NAME + " configuration was not found");
        }
    }

    @Provides
    @Singleton
    private ObjectStorageBlobsDAO buildObjectStore(ObjectStorageBlobConfiguration configuration, BlobId.Factory blobIdFactory) throws InterruptedException, ExecutionException, TimeoutException {
        ObjectStorageBlobsDAO dao = selectDaoBuilder(configuration)
            .container(configuration.getNamespace())
            .blobIdFactory(blobIdFactory)
            .build();
        dao.createContainer(configuration.getNamespace()).get(1, TimeUnit.MINUTES);
        return dao;
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName selectDaoBuilder(ObjectStorageBlobConfiguration configuration) {
        if (!configuration.getProvider().equals(OBJECTSTORAGE_PROVIDER_SWIFT)) {
            throw new IllegalArgumentException("unknown provider " + configuration.getProvider());
        }
        switch (configuration.getAuthApi()) {
            case SwiftTempAuthObjectStorage.AUTH_API_NAME:
                return ObjectStorageBlobsDAO.builder(configuration.getTempAuthConfiguration().get());
            case SwiftKeystone2ObjectStorage.AUTH_API_NAME:
                return ObjectStorageBlobsDAO.builder(configuration.getKeystone2Configuration().get());
            case SwiftKeystone3ObjectStorage.AUTH_API_NAME:
                return ObjectStorageBlobsDAO.builder(configuration.getKeystone3Configuration().get());
            default:
                throw new IllegalArgumentException("unknown auth api " + configuration.getAuthApi());
        }
    }

}
