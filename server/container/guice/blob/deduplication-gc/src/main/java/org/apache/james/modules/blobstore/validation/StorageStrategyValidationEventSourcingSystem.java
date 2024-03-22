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

package org.apache.james.modules.blobstore.validation;

import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.lifecycle.api.StartUpCheck.CheckResult;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class StorageStrategyValidationEventSourcingSystem {
    public static final String CHECK = "blobstore-storage-strategy-configuration-check";
    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public StorageStrategyValidationEventSourcingSystem(EventStore eventStore) {
        this.eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(new RegisterStorageStrategyCommandHandler(eventStore)),
            ImmutableSet.of(),
            eventStore);
    }

    public CheckResult validate(Supplier<StorageStrategy> storageStrategySupplier) {
        return Mono.from(eventSourcingSystem.dispatch(new RegisterStorageStrategy(storageStrategySupplier.get())))
            .thenReturn(CheckResult.builder()
                .checkName(CHECK)
                .resultType(StartUpCheck.ResultType.GOOD)
                .build())
            .onErrorResume(IllegalStateException.class,
                e -> Mono.just(CheckResult.builder()
                    .checkName(CHECK)
                    .resultType(StartUpCheck.ResultType.BAD)
                    .description(e.getMessage())
                    .build()))
            .block();
    }
}
