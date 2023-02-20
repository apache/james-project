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
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ForwardUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final RecipientRewriteTable rrt;

    @Inject
    public ForwardUsernameChangeTaskStep(RecipientRewriteTable rrt) {
        this.rrt = rrt;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        MappingSource oldSource = MappingSource.fromUser(oldUsername);
        MappingSource newSource = MappingSource.fromUser(newUsername);

        return migrateExistingForwards(oldSource, newSource)
            .then(redirectMailsToTheNewUsername(newUsername, oldSource))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    private Mono<Void> redirectMailsToTheNewUsername(Username newUsername, MappingSource oldSource) {
        return Mono.fromRunnable(Throwing.runnable(() -> rrt.addForwardMapping(oldSource, newUsername.asString())));
    }

    private Mono<Void> migrateExistingForwards(MappingSource oldSource, MappingSource newSource) {
        return Mono.fromCallable(() -> rrt.getStoredMappings(oldSource))
            .flatMapMany(mappings -> Flux.fromStream(mappings.asStream()))
            .filter(mapping -> mapping.getType().equals(Mapping.Type.Forward))
            .concatMap(mapping -> migrateExistingForward(oldSource, newSource, mapping))
            .then();
    }

    private Mono<Object> migrateExistingForward(MappingSource oldSource, MappingSource newSource, org.apache.james.rrt.lib.Mapping mapping) {
        if (mapping.getType().equals(Mapping.Type.Forward) && mapping.getMappingValue().equals(oldSource.asString())) {
            // self redirection to keep a copy
            return Mono.fromRunnable(Throwing.runnable(() -> rrt.addForwardMapping(newSource, newSource.asString())))
                .then(Mono.fromRunnable(Throwing.runnable(() -> rrt.removeMapping(oldSource, mapping))));
        }
        return Mono.fromRunnable(Throwing.runnable(() -> rrt.addForwardMapping(newSource, mapping.getMappingValue())))
            .then(Mono.fromRunnable(Throwing.runnable(() -> rrt.removeMapping(oldSource, mapping))));
    }

    @Override
    public StepName name() {
        return new StepName("ForwardUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 0;
    }
}
