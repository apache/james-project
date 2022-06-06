package org.apache.james.jmap.cassandra.upload;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.InputStream;

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

    @Inject
    public CassandraUploadRepository(UploadDAO uploadDAO, BlobStore blobStore, BucketNameGenerator bucketNameGenerator) {
        this.uploadDAO = uploadDAO;
        this.blobStore = blobStore;
        this.bucketNameGenerator = bucketNameGenerator;
    }

    @Override
    public Publisher<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        UploadId uploadId = generateId();
        UploadBucketName uploadBucketName = bucketNameGenerator.current();
        BucketName bucketName = uploadBucketName.asBucketName();

        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(countingInputStream -> Mono.from(blobStore.save(bucketName, countingInputStream, LOW_COST))
                .map(blobId -> new UploadDAO.UploadRepresentation(uploadId, bucketName, blobId, contentType, countingInputStream.getCount(), user))
                .flatMap(upload -> uploadDAO.save(upload)
                    .thenReturn(UploadMetaData.from(uploadId, upload.getContentType(), upload.getSize(), upload.getBlobId()))));
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        return uploadDAO.retrieve(id)
            .filter(upload -> upload.getUser().equals(user))
            .map(upload -> Upload.from(
                UploadMetaData.from(id, upload.getContentType(), upload.getSize(), upload.getBlobId()),
                () -> blobStore.read(upload.getBucketName(), upload.getBlobId(), LOW_COST)))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
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
