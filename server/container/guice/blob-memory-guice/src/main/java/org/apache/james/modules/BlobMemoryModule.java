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

package org.apache.james.modules;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;

public class BlobMemoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(HashBlobId.Factory.class).in(Scopes.SINGLETON);
        bind(BlobId.Factory.class).to(HashBlobId.Factory.class);

        bind(DeDuplicationBlobStore.class).in(Scopes.SINGLETON);
        bind(BlobStore.class).to(DeDuplicationBlobStore.class);

        bind(MemoryBlobStoreDAO.class).in(Scopes.SINGLETON);
        bind(BlobStoreDAO.class).to(MemoryBlobStoreDAO.class);

        bind(BucketName.class)
            .annotatedWith(Names.named(DeDuplicationBlobStore.DEFAULT_BUCKET()))
            .toInstance(BucketName.DEFAULT);
    }
}
