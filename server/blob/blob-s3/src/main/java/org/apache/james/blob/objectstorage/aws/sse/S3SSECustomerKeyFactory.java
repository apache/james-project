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

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public interface S3SSECustomerKeyFactory {

    record SSECustomerKey(String customerKey,
                          String md5,
                          String ssecAlgorithm) {
    }

    Publisher<SSECustomerKey> generate(BucketName bucketName, BlobId blobId);

    class SingleCustomerKeyFactory implements S3SSECustomerKeyFactory {

        private final SSECustomerKey sseCustomerKey;

        public SingleCustomerKeyFactory(S3SSECConfiguration.Basic sseCustomerConfiguration) throws InvalidKeySpecException, NoSuchAlgorithmException {
            S3SSECustomerKeyGenerator sseCustomerKeyGenerator = sseCustomerConfiguration.customerKeyFactoryAlgorithm()
                .map(Throwing.function(S3SSECustomerKeyGenerator::new))
                .orElseGet(Throwing.supplier(S3SSECustomerKeyGenerator::new));

            String customerKey = sseCustomerKeyGenerator.generateCustomerKey(sseCustomerConfiguration.masterPassword(), sseCustomerConfiguration.salt());
            String customerMd5 = sseCustomerKeyGenerator.generateCustomerKeyMd5(customerKey);
            this.sseCustomerKey = new SSECustomerKey(customerKey, customerMd5, sseCustomerConfiguration.ssecAlgorithm());
        }

        @Override
        public Mono<SSECustomerKey> generate(BucketName bucketName, BlobId blobId) {
            return Mono.just(sseCustomerKey);
        }
    }
}
