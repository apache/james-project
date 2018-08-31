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

package org.apache.james.blob.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.james.blob.api.BlobId;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.domain.Location;
import org.junit.jupiter.api.Test;

public interface ObjectStorageBlobsDAOContract {

    ObjectStorageBlobsDAOBuilder builder();

    Location DEFAULT_LOCATION = null;

    ContainerName containerName();

    @Test
    default void builtBlobsDAOCanStoreAndRetrieve() throws Exception {
        ObjectStorageBlobsDAOBuilder builder = builder();

        BlobStore blobStore = builder.getSupplier().get();
        blobStore.createContainerInLocation(DEFAULT_LOCATION, containerName().value());
        ObjectStorageBlobsDAO dao = builder.build();
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        CompletableFuture<BlobId> save = dao.save(bytes);
        InputStream inputStream = save.thenApply(dao::read).get(10, TimeUnit.SECONDS);
        assertThat(inputStream).hasSameContentAs(new ByteArrayInputStream(bytes));
    }
}
