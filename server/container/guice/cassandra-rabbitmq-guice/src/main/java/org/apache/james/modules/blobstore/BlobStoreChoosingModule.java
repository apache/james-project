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

import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.union.HybridBlobStore;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.modules.objectstorage.ObjectStorageDependenciesModule;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class BlobStoreChoosingModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new ObjectStorageDependenciesModule());

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraBlobModule.MODULE);
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
}
