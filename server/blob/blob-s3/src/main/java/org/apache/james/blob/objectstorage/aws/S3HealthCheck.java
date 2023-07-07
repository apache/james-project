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

import java.time.Duration;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class S3HealthCheck implements HealthCheck {

    private static final Integer HEALTH_CHECK_TIMEOUT = 5;

    private static final ComponentName COMPONENT_NAME = new ComponentName("S3");

    private final BlobStoreDAO blobStoreDAO;

    @Inject
    public S3HealthCheck(BlobStoreDAO blobStoreDAO) {
        this.blobStoreDAO = blobStoreDAO;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        Flux<BucketName> bucketNameFlux = Flux.from(blobStoreDAO.listBuckets())
                .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT));
        return bucketNameFlux.collect(Collectors.toList()).map(ele -> Result.healthy(COMPONENT_NAME))
                .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking S3", e)));
    }
}
