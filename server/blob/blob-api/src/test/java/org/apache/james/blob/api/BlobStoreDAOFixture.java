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
package org.apache.james.blob.api;

import java.nio.charset.StandardCharsets;

import com.google.common.base.Strings;

public interface BlobStoreDAOFixture {
    BucketName TEST_BUCKET_NAME = BucketName.of("my-test-bucket");
    BucketName CUSTOM_BUCKET_NAME = BucketName.of("custom");
    BlobId TEST_BLOB_ID = new TestBlobId("test-blob-id");
    BlobId OTHER_TEST_BLOB_ID = new TestBlobId("other-test-blob-id");
    String SHORT_STRING = "toto";
    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("2103456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    String TWELVE_MEGABYTES_STRING = Strings.repeat("7893456789\r\n", 1024 * 1024);
    byte[] TWELVE_MEGABYTES = TWELVE_MEGABYTES_STRING.getBytes(StandardCharsets.UTF_8);

}
