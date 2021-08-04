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

package org.apache.james.jmap.memory.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.mailbox.model.ContentType;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class InMemoryUploadRepository implements UploadRepository {

    private static final Map<UploadId, ImmutablePair<Username, UploadMetaData>> uploadStore = new HashMap<>();

    private final BlobStore blobStore;
    private final BucketName bucketName;

    @Inject
    public InMemoryUploadRepository(BlobStore blobStore) {
        this.blobStore = blobStore;
        this.bucketName = blobStore.getDefaultBucketName();
    }

    @Override
    public Publisher<UploadId> upload(InputStream data, ContentType contentType, Username user) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(contentType);
        Preconditions.checkNotNull(user);

        byte[] dataAsByte = toByteArray(data);
        return Mono.from(blobStore.save(bucketName, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
            .map(blobId -> {
                UploadId uploadId = UploadId.random();
                uploadStore.put(uploadId, new ImmutablePair<>(user, UploadMetaData.from(uploadId, contentType, dataAsByte.length, blobId)));
                return uploadId;
            });
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(user);

        return Mono.justOrEmpty(uploadStore.get(id))
            .filter(pair -> user.equals(pair.left))
            .flatMap(userAndMetaData -> retrieveUpload(userAndMetaData.right))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    private Mono<Upload> retrieveUpload(UploadMetaData uploadMetaData) {
        return Mono.from(blobStore.readBytes(bucketName, uploadMetaData.blobId()))
            .map(content -> Upload.from(uploadMetaData, () -> new ByteArrayInputStream(content)));
    }

    private byte[] toByteArray(InputStream inputStream) {
        try {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
