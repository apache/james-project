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

package org.apache.james.modules.blobstore;

import java.io.FileNotFoundException;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.union.UnionBlobStore;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.modules.objectstorage.ObjectStorageDependenciesModule;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class BlobStoreChoosingModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreChoosingModule.class);

    @Override
    protected void configure() {
        install(new ObjectStorageDependenciesModule());

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraBlobModule.MODULE);
    }

    @VisibleForTesting
    @Provides
    @Singleton
    BlobStoreChoosingConfiguration provideChoosingConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ConfigurationComponent.NAME);
            return BlobStoreChoosingConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using cassandra blobstore as the default");
            return BlobStoreChoosingConfiguration.cassandra();
        }
    }

    @VisibleForTesting
    @Provides
    @Named(MetricableBlobStore.BLOB_STORE_IMPLEMENTATION)
    @Singleton
    BlobStore provideBlobStore(BlobStoreChoosingConfiguration choosingConfiguration,
                               Provider<CassandraBlobsDAO> cassandraBlobStoreProvider,
                               Provider<ObjectStorageBlobsDAO> swiftBlobStoreProvider) {

        switch (choosingConfiguration.getImplementation()) {
            case OBJECTSTORAGE:
                return swiftBlobStoreProvider.get();
            case CASSANDRA:
                return cassandraBlobStoreProvider.get();
            case UNION:
                return UnionBlobStore.builder()
                    .current(swiftBlobStoreProvider.get())
                    .legacy(cassandraBlobStoreProvider.get())
                    .build();
            default:
                throw new RuntimeException(String.format("can not get the right blobstore provider with configuration %s",
                    choosingConfiguration.toString()));
        }
    }

}
