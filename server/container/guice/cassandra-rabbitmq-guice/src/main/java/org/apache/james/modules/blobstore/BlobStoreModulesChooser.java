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
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.union.HybridBlobStore;
import org.apache.james.modules.mailbox.CassandraBlobStoreDependenciesModule;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.modules.objectstorage.ObjectStorageDependenciesModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.name.Names;

public class BlobStoreModulesChooser {
    static class CassandraDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new CassandraBlobStoreDependenciesModule());
            bind(BlobStore.class)
                .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                .to(CassandraBlobStore.class);
        }
    }

    static class ObjectStorageDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new ObjectStorageDependenciesModule());
            bind(BlobStore.class)
                .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                .to(ObjectStorageBlobStore.class);
        }
    }

    static class HybridDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new ObjectStorageDependenciesModule());
            install(new CassandraBlobStoreDependenciesModule());
        }

        @Provides
        @Singleton
        @VisibleForTesting
        HybridBlobStore.Configuration providesHybridBlobStoreConfiguration(PropertiesProvider propertiesProvider) {
            try {
                Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
                return HybridBlobStore.Configuration.from(configuration);
            } catch (FileNotFoundException | ConfigurationException e) {
                return HybridBlobStore.Configuration.DEFAULT;
            }
        }

        @Provides
        @Named(CachedBlobStore.BACKEND)
        @Singleton
        BlobStore providesHybridBlobStore(HybridBlobStore.Configuration hybridBlobStoreConfiguration,
                                          CassandraBlobStore cassandraBlobStore,
                                          ObjectStorageBlobStore objectStorageBlobStore) {
            return HybridBlobStore.builder()
                .lowCost(objectStorageBlobStore)
                .highPerformance(cassandraBlobStore)
                .configuration(hybridBlobStoreConfiguration)
                .build();
        }
    }

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        switch (choosingConfiguration.getImplementation()) {
            case CASSANDRA:
                return ImmutableList.of(new CassandraDeclarationModule());
            case OBJECTSTORAGE:
                return ImmutableList.of(new ObjectStorageDeclarationModule());
            case HYBRID:
                return ImmutableList.of(new HybridDeclarationModule());
            default:
                throw new RuntimeException("Unsuported blobStore implementation " + choosingConfiguration.getImplementation());
        }
    }
}
