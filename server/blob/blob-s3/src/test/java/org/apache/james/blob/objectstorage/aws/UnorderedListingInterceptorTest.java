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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

class UnorderedListingInterceptorTest {
    private static final SdkHttpRequest HTTP_REQUEST = SdkHttpRequest.builder()
        .method(SdkHttpMethod.GET)
        .uri(URI.create("http://localhost:8080/bucket"))
        .build();

    private final UnorderedListingInterceptor testee = new UnorderedListingInterceptor();

    private SdkHttpRequest modify(SdkRequest request) {
        return testee.modifyHttpRequest(InterceptorContext.builder()
                .request(request)
                .httpRequest(HTTP_REQUEST)
                .build(),
            new ExecutionAttributes());
    }

    @Test
    void shouldAllowUnorderedListingForListObjectsV2() {
        assertThat(modify(ListObjectsV2Request.builder().bucket("bucket").build()).rawQueryParameters())
            .containsEntry("allow-unordered", List.of("true"));
    }

    @Test
    void shouldAllowUnorderedListingForListObjects() {
        assertThat(modify(ListObjectsRequest.builder().bucket("bucket").build()).rawQueryParameters())
            .containsEntry("allow-unordered", List.of("true"));
    }

    @Test
    void shouldNotAlterNonListingRequests() {
        assertThat(modify(GetObjectRequest.builder().bucket("bucket").key("key").build()).rawQueryParameters())
            .isEmpty();
    }

    @Test
    void shouldNotDuplicateTheParameterWhenAlreadyPresent() {
        SdkHttpRequest alreadySet = HTTP_REQUEST.toBuilder()
            .putRawQueryParameter("allow-unordered", "true")
            .build();

        SdkHttpRequest result = testee.modifyHttpRequest(InterceptorContext.builder()
                .request(ListObjectsV2Request.builder().bucket("bucket").build())
                .httpRequest(alreadySet)
                .build(),
            new ExecutionAttributes());

        assertThat(result.rawQueryParameters())
            .containsEntry("allow-unordered", List.of("true"));
    }
}
