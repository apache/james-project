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

package org.apache.james.blob.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface BlobTables {

    interface DefaultBucketBlobTable {
        String TABLE_NAME = "blobs";
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier NUMBER_OF_CHUNK = CqlIdentifier.fromCql("position");
    }

    interface DefaultBucketBlobParts {
        String TABLE_NAME = "blobParts";
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier CHUNK_NUMBER = CqlIdentifier.fromCql("chunkNumber");
        CqlIdentifier DATA = CqlIdentifier.fromCql("data");
    }

    interface BucketBlobTable {
        String TABLE_NAME = "blobsInBucket";
        CqlIdentifier BUCKET = CqlIdentifier.fromCql("bucket");
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier NUMBER_OF_CHUNK = CqlIdentifier.fromCql("position");
    }

    interface BucketBlobParts {
        String TABLE_NAME = "blobPartsInBucket";
        CqlIdentifier BUCKET = CqlIdentifier.fromCql("bucket");
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier CHUNK_NUMBER = CqlIdentifier.fromCql("chunkNumber");
        CqlIdentifier DATA = CqlIdentifier.fromCql("data");
    }

    interface BlobStoreCache {
        String TABLE_NAME = "blob_cache";
        CqlIdentifier ID = CqlIdentifier.fromCql("id");
        CqlIdentifier DATA = CqlIdentifier.fromCql("data");
        CqlIdentifier TTL_FOR_ROW = CqlIdentifier.fromCql("ttl");
    }
}
