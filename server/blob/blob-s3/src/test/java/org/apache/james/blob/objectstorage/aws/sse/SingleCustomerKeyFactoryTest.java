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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.TestBlobId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public class SingleCustomerKeyFactoryTest {
    private static S3SSECustomerKeyFactory.SingleCustomerKeyFactory testee;

    @BeforeAll
    static void setup() throws InvalidKeySpecException, NoSuchAlgorithmException {
        testee = new S3SSECustomerKeyFactory.SingleCustomerKeyFactory(new S3SSECConfiguration.Basic("AES256", "masterPassword", "salt"));

    }

    @Test
    void generateShouldReturnSSECustomerKey() {
        // When
        S3SSECustomerKeyFactory.SSECustomerKey sseCustomerKey = Mono.from(testee.generate(BucketName.of("bucket1"), new TestBlobId("blobId"))).block();

        // Then
        assertThat(sseCustomerKey.customerKey()).isEqualTo("tsY5n0n10fcpjm7aGArqbw231ptLGAF5ubxMxI2+QYw=");
        assertThat(sseCustomerKey.md5()).isEqualTo("G6IB1YLHiY/9uCh3TJhxzQ==");
        assertThat(sseCustomerKey.ssecAlgorithm()).isEqualTo("AES256");
    }

    @Test
    void generateShouldReturnSameSSECustomerKey() {
        // When
        S3SSECustomerKeyFactory.SSECustomerKey sseCustomerKey1 = Mono.from(testee.generate(BucketName.of("bucket1"), new TestBlobId("blobId"))).block();
        S3SSECustomerKeyFactory.SSECustomerKey sseCustomerKey2 = Mono.from(testee.generate(BucketName.of("bucket1"), new TestBlobId("blobId"))).block();
        S3SSECustomerKeyFactory.SSECustomerKey sseCustomerKey3 = Mono.from(testee.generate(BucketName.of("bucket3"), new TestBlobId("blobId3"))).block();

        // Then
        assertThat(sseCustomerKey1).isEqualTo(sseCustomerKey2)
            .isEqualTo(sseCustomerKey3);
    }
}