package org.apache.james.blob.cassandra.cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public class CachedDumbBlobStore implements DumbBlobStore {

    private final DumbBlobStoreCache cache;
    private final DumbBlobStore backend;

    @Inject
    public CachedDumbBlobStore(DumbBlobStoreCache cache, DumbBlobStore backend) {
        this.cache = cache;
        this.backend = backend;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        Preconditions.checkNotNull(bucketName, "bucketName should not be null");

        return Mono.from(cache.read(blobId))
            .map(bytes -> (InputStream) new ByteArrayInputStream(bytes))
            .switchIfEmpty(Mono.fromCallable(() -> backend.read(bucketName, blobId)))
            .blockOptional()
            .orElseThrow(() -> new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId)));
    }

    @Override
    public Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(cache.read(blobId))
            .switchIfEmpty(Mono.from(backend.readBytes(bucketName, blobId)));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Mono.from(cache.cache(blobId, data))
            .then(Mono.from(backend.save(bucketName, blobId, data)));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.fromCallable(() -> inputStream)
            .map(stream -> cache.cache(blobId, stream))
            .then(Mono.from(backend.save(bucketName, blobId, inputStream)));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.from(backend.save(bucketName, blobId, content))
            .then(Mono.using(content::openBufferedStream,
                inputStream -> Mono.from(cache.cache(blobId, inputStream)),
                Throwing.consumer(InputStream::close).sneakyThrow()));
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.delete(bucketName, blobId))
            .then(Mono.from(cache.remove(blobId)));
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.from(backend.deleteBucket(bucketName));
    }
}
