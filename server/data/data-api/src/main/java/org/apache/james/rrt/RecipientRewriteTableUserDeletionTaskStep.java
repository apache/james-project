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

package org.apache.james.rrt;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RecipientRewriteTableUserDeletionTaskStep implements DeleteUserDataTaskStep {
    private final RecipientRewriteTable rrt;

    @Inject
    public RecipientRewriteTableUserDeletionTaskStep(RecipientRewriteTable rrt) {
        this.rrt = rrt;
    }

    @Override
    public StepName name() {
        return new StepName("RecipientRewriteTableUserDeletionTaskStep");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Publisher<Void> deleteUserData(Username username) {
        return deleteRRT(username)
            .then(deleteForwards(username))
            .then(deleteGroup(username));
    }

    private Flux<Void> deleteRRT(Username username) {
        MappingSource mappingSource = MappingSource.fromUser(username);

        return Mono.fromCallable(() -> rrt.getStoredMappings(mappingSource))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .flatMapMany(mappings -> Flux.fromStream(mappings.asStream()))
            .flatMap(mapping -> deleteMapping(mappingSource, mapping));
    }

    private Mono<Void> deleteMapping(MappingSource mappingSource, Mapping mapping) {
        return Mono.fromRunnable(Throwing.runnable(() -> rrt.removeMapping(mappingSource, mapping)))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    private Mono<Void> deleteForwards(Username username) {
        return deleteSource(Mapping.forward(username.asString()));
    }

    private Mono<Void> deleteGroup(Username username) {
        return deleteSource(Mapping.group(username.asString()));
    }

    private Mono<Void> deleteSource(Mapping mapping) {
        return Mono.fromCallable(() -> rrt.listSources(mapping))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .flatMapMany(Flux::fromStream)
            .flatMap(source -> deleteMapping(source, mapping))
            .then();
    }
}
