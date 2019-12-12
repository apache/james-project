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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;


public interface ObjectStorageBlobStoreContract {

    String CONTENT = "content";

    BucketName defaultBucketName();

    default void assertBlobStoreCanStoreAndRetrieve(ObjectStorageBlobStoreBuilder.ReadyToBuild builder) {
        ObjectStorageBlobStore blobStore = builder.build();

        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), CONTENT).block();

        InputStream inputStream = blobStore.read(blobStore.getDefaultBucketName(), blobId);
        assertThat(inputStream).hasSameContentAs(IOUtils.toInputStream(CONTENT, StandardCharsets.UTF_8));
    }
}
