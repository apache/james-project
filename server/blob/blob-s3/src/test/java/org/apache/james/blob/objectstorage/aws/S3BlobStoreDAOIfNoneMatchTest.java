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

package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3BlobStoreDAOIfNoneMatchTest {
    private static final int CONDITIONAL_REQUEST_CONFLICT_STATUS_CODE = 409;
    private static final String CONDITIONAL_REQUEST_CONFLICT_ERROR_CODE = "ConditionalRequestConflict";
    private static final int PRECONDITION_FAILED_STATUS_CODE = 412;
    private static final String PRECONDITION_FAILED_ERROR_CODE = "PreconditionFailed";

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        return CompletableFuture.failedFuture(throwable);
    }

    private static S3Exception s3Exception(int statusCode, String errorCode) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(statusCode);
        builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build());
        return (S3Exception) builder.build();
    }

    private S3AsyncClient client;
    private S3BlobStoreDAO testee;

    @BeforeEach
    void setUp() {
        client = mock(S3AsyncClient.class);
        S3ClientFactory clientFactory = mock(S3ClientFactory.class);
        when(clientFactory.get()).thenReturn(client);

        S3BlobStoreConfiguration configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3AuthConfiguration.builder()
                .endpoint(URI.create("http://localhost"))
                .accessKeyId("access-key")
                .secretKey("secret-key")
                .build())
            .region(Region.of("us-east-1"))
            .build();

        testee = new S3BlobStoreDAO(clientFactory, configuration, new TestBlobId.Factory(),
            new S3RequestOption(S3RequestOption.DEFAULT.ssec(), true));
    }

    @Test
    void saveShouldRetryConditionalRequestConflict() {
        when(client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenReturn(failedFuture(s3Exception(CONDITIONAL_REQUEST_CONFLICT_STATUS_CODE, CONDITIONAL_REQUEST_CONFLICT_ERROR_CODE)))
            .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        assertThatCode(() -> Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block())
            .doesNotThrowAnyException();

        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void saveShouldTreatPreconditionFailedAfterConditionalRequestConflictAsSuccess() {
        when(client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenReturn(failedFuture(s3Exception(CONDITIONAL_REQUEST_CONFLICT_STATUS_CODE, CONDITIONAL_REQUEST_CONFLICT_ERROR_CODE)))
            .thenReturn(failedFuture(s3Exception(PRECONDITION_FAILED_STATUS_CODE, PRECONDITION_FAILED_ERROR_CODE)));

        assertThatCode(() -> Mono.from(testee.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block())
            .doesNotThrowAnyException();

        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }
}
