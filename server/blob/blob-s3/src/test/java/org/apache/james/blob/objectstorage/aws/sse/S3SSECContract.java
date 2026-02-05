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

package org.apache.james.blob.objectstorage.aws.sse;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BlobStoreDAO;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public interface S3SSECContract {

    BlobStoreDAO testee();

    S3AsyncClient s3Client();

    @Test
    default void getObjectShouldFailWhenNotDefineCustomerKeyInHeaderRequest() {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThatThrownBy(() -> s3Client().getObject(GetObjectRequest.builder()
                .bucket(TEST_BUCKET_NAME.asString())
                .key(TEST_BLOB_ID.asString())
                .build(), AsyncResponseTransformer.toBytes())
            .thenApply(BytesWrapper::asByteArray)
            .get())
            .hasMessageContaining("Bad request: Object is encrypted");
    }

    @Test
    default void getObjectShouldFailWhenInvalidCustomerKeyInRequest() {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThatThrownBy(() -> s3Client().getObject(GetObjectRequest.builder()
                .bucket(TEST_BUCKET_NAME.asString())
                .key(TEST_BLOB_ID.asString())
                .sseCustomerKey("invalid")
                .sseCustomerKeyMD5("123")
                .build(), AsyncResponseTransformer.toBytes())
            .thenApply(BytesWrapper::asByteArray)
            .get())
            .hasMessageContaining("Bad request: Unexpected server-side-encryption-customer-key{,-md5} header(s)");
    }

    @Test
    default void getObjectShouldFailWhenNotMatchedCustomerKeyInRequest() throws Exception {
        Mono.from(testee().save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        S3SSECustomerKeyGenerator sseCustomerKeyGenerator = new S3SSECustomerKeyGenerator();

        String customerKey = sseCustomerKeyGenerator.generateCustomerKey("random1", "salt123random");
        String customerKeyMd5 = sseCustomerKeyGenerator.generateCustomerKeyMd5(customerKey);

        assertThatThrownBy(() -> s3Client().getObject(GetObjectRequest.builder()
                .bucket(TEST_BUCKET_NAME.asString())
                .key(TEST_BLOB_ID.asString())
                .sseCustomerKey(customerKey)
                .sseCustomerKeyMD5(customerKeyMd5)
                .sseCustomerAlgorithm("AES256")
                .build(), AsyncResponseTransformer.toBytes())
            .thenApply(BytesWrapper::asByteArray)
            .get())
            .hasMessageContaining("Bad request: Invalid encryption key, could not decrypt object metadata");
    }
}
