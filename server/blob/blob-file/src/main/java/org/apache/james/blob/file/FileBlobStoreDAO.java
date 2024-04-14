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

package org.apache.james.blob.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.filesystem.api.FileSystem;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class FileBlobStoreDAO implements BlobStoreDAO {

    private final File root;
    private final  BlobId.Factory blobIdFactory;

    @Inject
    public FileBlobStoreDAO(FileSystem fileSystem, BlobId.Factory blobIdFactory) throws FileNotFoundException {
        root = fileSystem.getFile("file://var/blob");
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        File bucketRoot = getBucketRoot(bucketName);
        File blob = new File(bucketRoot, blobId.asString());
        try {
            return new FileInputStream(blob);
        } catch (FileNotFoundException e) {
            throw new ObjectNotFoundException(String.format("Cannot locate %s within %s", blobId.asString(), bucketName.asString()), e);
        }
    }

    private File getBucketRoot(BucketName bucketName) {
        File bucketRoot = new File(root, bucketName.asString());
        if (!bucketRoot.exists()) {
            try {
                FileUtils.forceMkdir(bucketRoot);
            } catch (IOException e) {
                throw new ObjectStoreIOException("Cannot create bucket", e);
            }
        }
        return bucketRoot;
    }

    @Override
    public Mono<InputStream> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> read(bucketName, blobId))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> {
            File bucketRoot = getBucketRoot(bucketName);
            File blob = new File(bucketRoot, blobId.asString());
            return FileUtils.readFileToByteArray(blob);
        }).onErrorResume(NoSuchFileException.class, e -> Mono.error(new ObjectNotFoundException(String.format("Cannot locate %s within %s", blobId.asString(), bucketName.asString()), e)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(data);

        return Mono.fromRunnable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                save(data, blob);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);
        return Mono.fromRunnable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                save(inputStream, blob);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then()
            .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                .filter(e -> e instanceof OverlappingFileLockException));
    }

    private void save(InputStream inputStream, File blob) {
        if (blob.exists()) {
            return;
        }

        try (FileOutputStream out = new FileOutputStream(blob);
             FileChannel channel = out.getChannel();
             FileLock fileLock = channel.lock()) {
            inputStream.transferTo(out);
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occured", e);
        }
    }

    private void save(byte[] data, File blob) {
        if (blob.exists()) {
            return;
        }

        try (FileOutputStream out = new FileOutputStream(blob);
             FileChannel channel = out.getChannel();
             FileLock fileLock = channel.lock()) {
            out.write(data);
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occured", e);
        }
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.fromCallable(() -> {
                try {
                    return content.read();
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occured", e);
                }
            })
            .flatMap(bytes -> save(bucketName, blobId, bytes));
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);

        return Mono.fromRunnable(Throwing.runnable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                FileUtils.deleteQuietly(blob);
            }))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Flux.fromIterable(blobIds)
            .flatMap(id -> delete(bucketName, id))
            .then();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.fromRunnable(Throwing.runnable(() -> {
                File bucketRoot = new File(root, bucketName.asString());
                FileUtils.deleteQuietly(bucketRoot);
            }))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return Mono.fromCallable(() -> Files.list(root.toPath()))
            .flatMapMany(Flux::fromStream)
            .map(path -> BucketName.of(path.getFileName().toString()))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(NoSuchFileException.class, e -> Flux.empty());
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return Mono.fromCallable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                return Files.list(bucketRoot.toPath());
            })
            .flatMapMany(Flux::fromStream)
            .map(path -> blobIdFactory.from(path.getFileName().toString()))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
