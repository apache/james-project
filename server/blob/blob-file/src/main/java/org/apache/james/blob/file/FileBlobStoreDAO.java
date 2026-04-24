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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.filesystem.api.FileSystem;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class FileBlobStoreDAO implements BlobStoreDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBlobStoreDAO.class);
    private static final String JAMES_BLOB_METADATA_ATTRIBUTE_PREFIX = "james-blob-metadata-";

    private final File root;
    private final  BlobId.Factory blobIdFactory;

    @Inject
    public FileBlobStoreDAO(FileSystem fileSystem, BlobId.Factory blobIdFactory) throws FileNotFoundException {
        root = fileSystem.getFile("file://var/blob");
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        File bucketRoot = getBucketRoot(bucketName);
        File blob = new File(bucketRoot, blobId.asString());
        try {
            return InputStreamBlob.of(new FileInputStream(blob), readMetadata(blob.toPath()));
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
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> read(bucketName, blobId))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                return BytesBlob.of(FileUtils.readFileToByteArray(blob), readMetadata(blob.toPath()));
            }).onErrorResume(NoSuchFileException.class, e -> Mono.error(new ObjectNotFoundException(String.format("Cannot locate %s within %s", blobId.asString(), bucketName.asString()), e)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        return switch (blob) {
            case BytesBlob bytesBlob -> save(bucketName, blobId, bytesBlob.payload(), bytesBlob.metadata());
            case InputStreamBlob inputStreamBlob -> save(bucketName, blobId, inputStreamBlob.payload(), inputStreamBlob.metadata());
            case ByteSourceBlob byteSourceBlob -> save(bucketName, blobId, byteSourceBlob.payload(), byteSourceBlob.metadata());
        };
    }

    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data, BlobMetadata metadata) {
        Preconditions.checkNotNull(data);

        return Mono.fromRunnable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                save(data, blob, metadata);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream, BlobMetadata metadata) {
        Preconditions.checkNotNull(inputStream);
        return Mono.fromRunnable(() -> {
                File bucketRoot = getBucketRoot(bucketName);
                File blob = new File(bucketRoot, blobId.asString());
                save(inputStream, blob, metadata);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then()
            .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                .filter(e -> e instanceof OverlappingFileLockException));
    }

    private void save(InputStream inputStream, File blob, BlobMetadata metadata) {
        File tempFile = createTempFile(blob);
        boolean tempFileHandled = false;
        try {
            // Overwrites blob (and its metadata) should be supported. Prepare payload and metadata on a temp file first,
            // then atomically replace the target blob so concurrent saves do not expose partially written data.
            writeToTempFile(tempFile, inputStream, metadata);
            replaceBlob(tempFile, blob);
            tempFileHandled = true;
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occurred", e);
        } finally {
            if (!tempFileHandled) {
                FileUtils.deleteQuietly(tempFile);
            }
        }
    }

    private void save(byte[] data, File blob, BlobMetadata metadata) {
        save(new ByteArrayInputStream(data), blob, metadata);
    }

    private void writeToTempFile(File tempFile, InputStream inputStream, BlobMetadata metadata) throws IOException {
        try (FileOutputStream out = new FileOutputStream(tempFile);
             FileChannel channel = out.getChannel();
             FileLock fileLock = channel.lock()) {
            inputStream.transferTo(out);
            writeMetadata(tempFile.toPath(), metadata);
        }
    }

    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content, BlobMetadata metadata) {
        return Mono.fromCallable(() -> {
                try {
                    return content.read();
                } catch (IOException e) {
                    throw new ObjectStoreIOException("IOException occurred", e);
                }
            })
            .flatMap(bytes -> save(bucketName, blobId, bytes, metadata));
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
            .map(path -> blobIdFactory.parse(path.getFileName().toString()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private BlobMetadata readMetadata(Path path) {
        UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        if (attributeView == null) {
            return BlobMetadata.empty();
        }

        try {
            return new BlobMetadata(attributeView.list().stream()
                .filter(this::isBlobMetadataAttribute)
                .collect(ImmutableMap.toImmutableMap(
                    this::asBlobMetadataName,
                    Throwing.function((String attributeName) -> new BlobMetadataValue(readFileAttributeValue(attributeView, attributeName)))
                        .sneakyThrow())));
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occurred", e);
        }
    }

    private void writeMetadata(Path path, BlobMetadata metadata) throws IOException {
        if (metadata.equals(BlobMetadata.empty())) {
            return;
        }

        UserDefinedFileAttributeView attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        if (attributeView == null) {
            LOGGER.warn("Skipping blob metadata persistence for {} because user defined file attributes are not supported by the file system", path);
            return;
        }
        writeMetadata(attributeView, metadata);
    }

    private void writeMetadata(UserDefinedFileAttributeView attributeView, BlobMetadata metadata) throws IOException {
        for (Map.Entry<BlobMetadataName, BlobMetadataValue> entry : metadata.underlyingMap().entrySet()) {
            attributeView.write(asFileAttributeName(entry.getKey()), StandardCharsets.UTF_8.encode(entry.getValue().value()));
        }
    }

    private boolean isBlobMetadataAttribute(String extendedAttributeName) {
        return extendedAttributeName.startsWith(JAMES_BLOB_METADATA_ATTRIBUTE_PREFIX);
    }

    private String asFileAttributeName(BlobMetadataName metadataName) {
        return JAMES_BLOB_METADATA_ATTRIBUTE_PREFIX + metadataName.name();
    }

    private BlobMetadataName asBlobMetadataName(String attributeName) {
        return new BlobMetadataName(attributeName.substring(JAMES_BLOB_METADATA_ATTRIBUTE_PREFIX.length()));
    }

    private String readFileAttributeValue(UserDefinedFileAttributeView attributeView, String attributeName) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(attributeView.size(attributeName));
        attributeView.read(attributeName, byteBuffer);
        byteBuffer.flip();
        return StandardCharsets.UTF_8.decode(byteBuffer).toString();
    }

    private File createTempFile(File blob) {
        try {
            return Files.createTempFile(blob.getParentFile().toPath(), blob.getName(), ".tmp").toFile();
        } catch (IOException e) {
            throw new ObjectStoreIOException("IOException occurred", e);
        }
    }

    private void replaceBlob(File tempFile, File target) throws IOException {
        Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

}
