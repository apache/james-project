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
package org.apache.james.jmap.cassandra.upload;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.InputStream;
import java.time.Clock;

import javax.inject.Inject;

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

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.io.CountingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraUploadRepository implements UploadRepository {
    private final UploadDAO uploadDAO;
    private final BlobStore blobStore;
    private final BucketNameGenerator bucketNameGenerator;
    private final Clock clock;

    @Inject
    public CassandraUploadRepository(UploadDAO uploadDAO, BlobStore blobStore, BucketNameGenerator bucketNameGenerator, Clock clock) {
        this.uploadDAO = uploadDAO;
        this.blobStore = blobStore;
        this.bucketNameGenerator = bucketNameGenerator;
        this.clock = clock;
    }

    @Override
    public Publisher<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        UploadId uploadId = generateId();
        UploadBucketName uploadBucketName = bucketNameGenerator.current();
        BucketName bucketName = uploadBucketName.asBucketName();

        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(countingInputStream -> Mono.from(blobStore.save(bucketName, countingInputStream, LOW_COST))
                .map(blobId -> new UploadDAO.UploadRepresentation(uploadId, bucketName, blobId, contentType, countingInputStream.getCount(), user, clock.instant()))
                .flatMap(upload -> uploadDAO.save(upload)
                    .thenReturn(upload.toUploadMetaData())));
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        return uploadDAO.retrieve(user, id)
            .map(upload -> Upload.from(upload.toUploadMetaData(),
                () -> blobStore.read(upload.getBucketName(), upload.getBlobId(), LOW_COST)))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    @Override
    public Publisher<Void> delete(UploadId id, Username user) {
        return uploadDAO.delete(user, id);
    }

    @Override
    public Publisher<UploadMetaData> listUploads(Username user) {
        return uploadDAO.list(user)
            .map(UploadDAO.UploadRepresentation::toUploadMetaData);
    }

    public Mono<Void> purge() {
        return Flux.from(blobStore.listBuckets())
            .<UploadBucketName>handle((bucketName, sink) -> UploadBucketName.ofBucket(bucketName).ifPresentOrElse(sink::next, sink::complete))
            .filter(bucketNameGenerator.evictionPredicate())
            .concatMap(bucket -> blobStore.deleteBucket(bucket.asBucketName()))
            .then();
    }

    private UploadId generateId() {
        return UploadId.from(Uuids.timeBased());
    }
}
