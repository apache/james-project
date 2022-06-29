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

package org.apache.james.mailrepository.api;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.util.FunctionalUtils;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EmptyErrorMailRepositoryHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("EmptyErrorMailRepository");
    private final MailRepositoryStore repositoryStore;
    private final MailRepositoryPath errorRepositoryPath;

    public EmptyErrorMailRepositoryHealthCheck(MailRepositoryPath errorRepositoryPath, MailRepositoryStore repositoryStore) {
        this.repositoryStore = repositoryStore;
        this.errorRepositoryPath = errorRepositoryPath;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromStream(Throwing.supplier(() -> repositoryStore.getByPath(errorRepositoryPath)))
            .flatMap(MailRepository::sizeReactive)
            .any(repositorySize -> repositorySize > 0)
            .filter(FunctionalUtils.identityPredicate())
            .map(hasSize -> Result.degraded(COMPONENT_NAME, "MailRepository is not empty"))
            .switchIfEmpty(Mono.just(Result.healthy(COMPONENT_NAME)));
    }
}
