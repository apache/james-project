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

import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * Ceph RADOS Gateway supports an {@code allow-unordered} extension on bucket listings: rather than merging the
 * results of every bucket shard in order, the gateway returns the entries of each shard as they come, which is
 * significantly cheaper on sharded buckets.
 *
 * James never relies on the ordering of {@link org.apache.james.blob.api.BlobStoreDAO#listBlobs} results, thus
 * unordered listing is a safe trade for deployments backed by RADOS.
 *
 * Note that RADOS rejects {@code allow-unordered} combined with a {@code delimiter}: James never sets one.
 */
public class UnorderedListingInterceptor implements ExecutionInterceptor {
    private static final String ALLOW_UNORDERED = "allow-unordered";

    @Override
    public SdkHttpRequest modifyHttpRequest(Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
        if (context.request() instanceof ListObjectsV2Request || context.request() instanceof ListObjectsRequest) {
            return context.httpRequest()
                .toBuilder()
                .putRawQueryParameter(ALLOW_UNORDERED, "true")
                .build();
        }
        return context.httpRequest();
    }
}
