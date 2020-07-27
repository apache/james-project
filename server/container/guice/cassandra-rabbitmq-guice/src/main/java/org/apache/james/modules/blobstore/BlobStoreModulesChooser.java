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

import java.util.List;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.cassandra.CassandraDumbBlobStore;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.modules.mailbox.CassandraBlobStoreDependenciesModule;
import org.apache.james.modules.objectstorage.ObjectStorageDependenciesModule;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;

public class BlobStoreModulesChooser {
    static class CassandraDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new CassandraBlobStoreDependenciesModule());

            bind(DumbBlobStore.class).to(CassandraDumbBlobStore.class);

            bind(BlobStore.class)
                .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                .to(DeDuplicationBlobStore.class);
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

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        switch (choosingConfiguration.getImplementation()) {
            case CASSANDRA:
                return ImmutableList.of(new CassandraDeclarationModule());
            case OBJECTSTORAGE:
                return ImmutableList.of(new ObjectStorageDeclarationModule());
            default:
                throw new RuntimeException("Unsuported blobStore implementation " + choosingConfiguration.getImplementation());
        }
    }
}
