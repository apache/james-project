package org.apache.james.xodus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.StoreTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class XodusBlobStoreDAO implements BlobStoreDAO {
    static final String ENTITY_TYPE = "JamesBlob";
    private static final String PROP_BUCKET = "bucket";
    private static final String PROP_BLOB_ID = "blobId";
    private static final String PROP_KEY = "bucketAndBlobId";
    private static final String BLOB_PROPERTY = "payload";

    private final PersistentEntityStore entityStore;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public XodusBlobStoreDAO(PersistentEntityStore entityStore, BlobId.Factory blobIdFactory) {
        this.entityStore = entityStore;
        this.blobIdFactory = blobIdFactory;
    }

    private String buildKey(BucketName bucketName, BlobId blobId) {
        return bucketName.asString() + "/" + blobId.asString();
    }

    private Entity findEntity(StoreTransaction txn, BucketName bucketName, BlobId blobId) {
        Entity entity = txn.find(ENTITY_TYPE, PROP_KEY, buildKey(bucketName, blobId)).getFirst();
        if (entity != null) {
            return entity;
        }
        // Fallback for existing legacy entries without PROP_KEY
        EntityIterable entities = txn.find(ENTITY_TYPE, PROP_BLOB_ID, blobId.asString());
        for (Entity e : entities) {
            Comparable<?> bucketProp = e.getProperty(PROP_BUCKET);
            if (bucketProp != null && bucketName.asString().equals(bucketProp.toString())) {
                return e;
            }
        }
        return null;
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.from(readReactive(bucketName, blobId)).block();
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return Mono.fromCallable(() -> {
            return entityStore.computeInReadonlyTransaction(txn -> {
                Entity entity = findEntity(txn, bucketName, blobId);
                if (entity == null) {
                    return null;
                }
                InputStream blobStream = entity.getBlob(BLOB_PROPERTY);
                if (blobStream == null) {
                    blobStream = new ByteArrayInputStream(new byte[0]);
                }
                try {
                    // Read into byte array while inside transaction
                    byte[] bytes = blobStream.readAllBytes();
                    return InputStreamBlob.of(new ByteArrayInputStream(bytes));
                } catch (IOException e) {
                    throw new RuntimeException(new ObjectStoreIOException("Error reading blob stream", e));
                }
            });
        }).switchIfEmpty(Mono.error(() -> new ObjectNotFoundException("Blob not found: " + blobId.asString() + " in bucket: " + bucketName.asString())));
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(readReactive(bucketName, blobId))
            .flatMap(inputStreamBlob -> Mono.fromCallable(inputStreamBlob::asBytes));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        Preconditions.checkNotNull(blob);
        return Mono.fromRunnable(() -> {
            try {
                InputStream is = blob.asInputStream().payload();
                entityStore.executeInTransaction(txn -> {
                    Entity entity = findEntity(txn, bucketName, blobId);
                    if (entity == null) {
                        entity = txn.newEntity(ENTITY_TYPE);
                        entity.setProperty(PROP_BUCKET, bucketName.asString());
                        entity.setProperty(PROP_BLOB_ID, blobId.asString());
                        entity.setProperty(PROP_KEY, buildKey(bucketName, blobId));
                    } else if (entity.getProperty(PROP_KEY) == null) {
                        entity.setProperty(PROP_KEY, buildKey(bucketName, blobId));
                    }
                    entity.setBlob(BLOB_PROPERTY, is);
                });
            } catch (Exception e) {
                throw new ObjectStoreIOException("Error saving blob " + blobId.asString(), e);
            }
        });
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.fromRunnable(() -> {
            entityStore.executeInTransaction(txn -> {
                Entity entity = findEntity(txn, bucketName, blobId);
                if (entity != null) {
                    entity.delete();
                }
            });
        });
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Mono.fromRunnable(() -> {
            entityStore.executeInTransaction(txn -> {
                for (BlobId blobId : blobIds) {
                    Entity entity = findEntity(txn, bucketName, blobId);
                    if (entity != null) {
                        entity.delete();
                    }
                }
            });
        });
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.fromRunnable(() -> {
            entityStore.executeInTransaction(txn -> {
                EntityIterable entities = txn.find(ENTITY_TYPE, PROP_BUCKET, bucketName.asString());
                for (Entity entity : entities) {
                    entity.delete();
                }
            });
        });
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return Mono.fromCallable(() -> {
            Set<BucketName> buckets = new HashSet<>();
            entityStore.executeInReadonlyTransaction(txn -> {
                EntityIterable entities = txn.getAll(ENTITY_TYPE);
                for (Entity entity : entities) {
                    Comparable<?> b = entity.getProperty(PROP_BUCKET);
                    if (b != null) {
                        buckets.add(BucketName.of(b.toString()));
                    }
                }
            });
            return buckets;
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return Mono.fromCallable(() -> {
            Set<BlobId> blobIds = new HashSet<>();
            entityStore.executeInReadonlyTransaction(txn -> {
                EntityIterable entities = txn.find(ENTITY_TYPE, PROP_BUCKET, bucketName.asString());
                for (Entity entity : entities) {
                    Comparable<?> b = entity.getProperty(PROP_BLOB_ID);
                    if (b != null) {
                        blobIds.add(blobIdFactory.of(b.toString()));
                    }
                }
            });
            return blobIds;
        }).flatMapMany(Flux::fromIterable);
    }
}
