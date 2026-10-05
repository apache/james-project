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

package org.apache.james.xodus;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.apache.james.blob.zstd.ZstdBlobStoreDAO;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class XodusBlobModule extends AbstractModule {
    private static final String XODUS_RAW = "xodusRaw";

    @Override
    protected void configure() {
        bind(PlainBlobId.Factory.class).in(Scopes.SINGLETON);
        bind(BlobId.Factory.class).to(PlainBlobId.Factory.class);

        bind(DeDuplicationBlobStore.class).in(Scopes.SINGLETON);
        bind(BlobStore.class).to(DeDuplicationBlobStore.class);

        bind(XodusBlobStoreDAO.class).in(Scopes.SINGLETON);
        bind(BlobStoreDAO.class).annotatedWith(Names.named(XODUS_RAW)).to(XodusBlobStoreDAO.class);

        bind(BucketName.class)
            .annotatedWith(Names.named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER))
            .toInstance(BucketName.DEFAULT);
    }

    @Provides
    @Singleton
    BlobStoreDAO provideZstdBlobStoreDAO(@Named(XODUS_RAW) BlobStoreDAO rawDao, MetricFactory metricFactory) {
        return new ZstdBlobStoreDAO(rawDao, CompressionConfiguration.DEFAULT, metricFactory);
    }
}
