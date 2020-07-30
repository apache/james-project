/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.server.blob.deduplication;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;

public abstract class BlobStoreFactory {
    @FunctionalInterface
    public interface RequireDumbBlobStore {
        RequireBlobIdFactory dumbBlobStore(DumbBlobStore dumbBlobStore);
    }

    @FunctionalInterface
    public interface RequireBlobIdFactory {
        RequireBucketName blobIdFactory(BlobId.Factory blobIdFactory);
    }

    @FunctionalInterface
    public interface RequireBucketName {
        RequireStoringStrategy bucket(BucketName defaultBucketName);

        default RequireStoringStrategy defaultBucketName() {
            return bucket(BucketName.DEFAULT);
        }
    }

    @FunctionalInterface
    public interface RequireStoringStrategy {
        BlobStore strategy(StorageStrategy storageStrategy);

        default BlobStore passthrough() {
            return strategy(StorageStrategy.PASSTHROUGH);
        }

        default BlobStore deduplication() {
            return strategy(StorageStrategy.DEDUPLICATION);
        }
    }

    public static RequireDumbBlobStore builder() {
        return dumbBlobStore -> blobIdFactory -> defaultBucketName -> storageStrategy -> {
            switch (storageStrategy) {
                case PASSTHROUGH:
                    return new PassThroughBlobStore(dumbBlobStore, defaultBucketName, blobIdFactory);
                case DEDUPLICATION:
                    return new DeDuplicationBlobStore(dumbBlobStore, defaultBucketName, blobIdFactory);
                default:
                    throw new IllegalArgumentException("Unknown storage strategy");
            }
        };
    }
}
