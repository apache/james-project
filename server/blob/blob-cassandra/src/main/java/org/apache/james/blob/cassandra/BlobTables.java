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

public interface BlobTables {

    interface DefaultBucketBlobTable {
        String TABLE_NAME = "blobs";
        String ID = "id";
        String NUMBER_OF_CHUNK = "position";
    }

    interface DefaultBucketBlobParts {
        String TABLE_NAME = "blobParts";
        String ID = "id";
        String CHUNK_NUMBER = "chunkNumber";
        String DATA = "data";
    }

    interface BucketBlobTable {
        String TABLE_NAME = "blobsInBucket";
        String BUCKET = "bucket";
        String ID = "id";
        String NUMBER_OF_CHUNK = "position";
    }

    interface BucketBlobParts {
        String TABLE_NAME = "blobPartsInBucket";
        String BUCKET = "bucket";
        String ID = "id";
        String CHUNK_NUMBER = "chunkNumber";
        String DATA = "data";
    }

    interface BlobStoreCache {
        String TABLE_NAME = "blob_cache";
        String ID = "id";
        String DATA = "data";
        String TTL_FOR_ROW = "ttl";
    }
}
