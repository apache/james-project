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

import java.nio.charset.StandardCharsets;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;

public interface CassandraBlobStoreFixture {
    byte[] DATA = "anydata".getBytes(StandardCharsets.UTF_8);
    byte[] DATA_2 = "anydata2".getBytes(StandardCharsets.UTF_8);
    int POSITION = 42;
    int POSITION_2 = 43;
    int NUMBER_OF_CHUNK = 17;
    int NUMBER_OF_CHUNK_2 = 18;
    BlobId BLOB_ID = new PlainBlobId.Factory().parse("05dcb33b-8382-4744-923a-bc593ad84d23");
    BlobId BLOB_ID_2 = new PlainBlobId.Factory().parse("05dcb33b-8382-4744-923a-bc593ad84d24");
    BucketName BUCKET_NAME = BucketName.of("aBucket");
    BucketName BUCKET_NAME_2 = BucketName.of("anotherBucket");
}
