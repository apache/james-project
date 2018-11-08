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
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class ObjectStorageBlobsDAOProvider implements Provider<ObjectStorageBlobsDAO> {
    static final String OBJECTSTORAGE_CONFIGURATION_NAME = "objectstorage";

    static final String OBJECTSTORAGE_NAMESPACE = "objectstorage.namespace";
    static final String OBJECTSTORAGE_PROVIDER = "objectstorage.provider";
    static final String OBJECTSTORAGE_SWIFT_AUTH_API = "objectstorage.swift.authapi";

    public static final String OBJECTSTORAGE_PROVIDER_SWIFT = "swift";

    private final Configuration configuration;
    private final BlobId.Factory blobIdFactory;
    private final ImmutableMap<String, Supplier<ObjectStorageBlobsDAOBuilder.RequireContainerName>> providersByName;
    private final ImmutableMap<String, Supplier<ObjectStorageBlobsDAOBuilder.RequireContainerName>> swiftAuthApiByName;

    @Inject
    public ObjectStorageBlobsDAOProvider(PropertiesProvider propertiesProvider,
                                         BlobId.Factory blobIdFactory) throws ConfigurationException {
        //This provider map will allow to implement S3 provider
        providersByName = ImmutableMap.of(OBJECTSTORAGE_PROVIDER_SWIFT, this::selectSwiftAuthApi);
        swiftAuthApiByName = ImmutableMap.of(
            SwiftTempAuthObjectStorage.AUTH_API_NAME, this::swiftTempAuth,
            SwiftKeystone2ObjectStorage.AUTH_API_NAME, this::swiftKeystone2Auth,
            SwiftKeystone3ObjectStorage.AUTH_API_NAME, this::swiftKeystone3Auth);

        this.blobIdFactory = blobIdFactory;
        try {
            this.configuration = propertiesProvider.getConfiguration(OBJECTSTORAGE_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException(OBJECTSTORAGE_CONFIGURATION_NAME + " configuration " +
                "was not found");
        }
    }

    @Override
    public ObjectStorageBlobsDAO get() {
        String provider = configuration.getString(OBJECTSTORAGE_PROVIDER, null);
        String namespace = configuration.getString(OBJECTSTORAGE_NAMESPACE, null);
        Preconditions.checkArgument(provider != null,
            "Mandatory configuration value " + OBJECTSTORAGE_PROVIDER + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        Preconditions.checkArgument(namespace != null,
            "Mandatory configuration value " + OBJECTSTORAGE_NAMESPACE + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");

        return providersByName.get(provider).get().container(ContainerName.of(namespace)).blobIdFactory(blobIdFactory).build();
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName selectSwiftAuthApi() {
        String authApi = configuration.getString(OBJECTSTORAGE_SWIFT_AUTH_API, null);
        Preconditions.checkArgument(authApi != null,
            "Mandatory configuration value " + OBJECTSTORAGE_PROVIDER + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        return swiftAuthApiByName.get(authApi).get();
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName swiftTempAuth() {
        return ObjectStorageBlobsDAO.builder(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration));
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName swiftKeystone2Auth() {
        return ObjectStorageBlobsDAO.builder(SwiftKeystone2ConfigurationReader.readSwiftConfiguration(configuration));
    }

    private ObjectStorageBlobsDAOBuilder.RequireContainerName swiftKeystone3Auth() {
        return ObjectStorageBlobsDAO.builder(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration));
    }
}
