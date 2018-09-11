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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.blob.api.BlobId;


public interface ObjectStorageBlobsDAOContract {

    ContainerName containerName();

    default void assertBlobsDAOCanStoreAndRetrieve(ObjectStorageBlobsDAOBuilder builder)
        throws InterruptedException, ExecutionException, TimeoutException {
        ObjectStorageBlobsDAO dao = builder.build();
        dao.createContainer(containerName());
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        CompletableFuture<BlobId> save = dao.save(bytes);
        InputStream inputStream = save.thenApply(dao::read).get(10, TimeUnit.SECONDS);
        assertThat(inputStream).hasSameContentAs(new ByteArrayInputStream(bytes));
    }
}
