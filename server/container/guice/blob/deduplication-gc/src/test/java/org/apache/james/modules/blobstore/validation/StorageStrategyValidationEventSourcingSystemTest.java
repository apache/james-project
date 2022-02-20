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

import static org.apache.james.server.blob.deduplication.StorageStrategy.DEDUPLICATION;
import static org.apache.james.server.blob.deduplication.StorageStrategy.PASSTHROUGH;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class StorageStrategyValidationEventSourcingSystemTest {
    private StorageStrategyValidationEventSourcingSystem testee;
    private InMemoryEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        testee = new StorageStrategyValidationEventSourcingSystem(eventStore);
    }

    @Test
    void startingForTheFirstTimeShouldSucceedWhenPassThrough() {
        StartUpCheck.CheckResult checkResult = testee.validate(() -> PASSTHROUGH);

        assertThat(checkResult.getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    @Test
    void startingForTheFirstTimeShouldSucceedWhenDeduplication() {
        StartUpCheck.CheckResult checkResult = testee.validate(() -> DEDUPLICATION);

        assertThat(checkResult.getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    @Test
    void startingShouldSucceedWhenTurningOnDeduplication() {
        testee.validate(() -> PASSTHROUGH);

        StartUpCheck.CheckResult checkResult = testee.validate(() -> DEDUPLICATION);

        assertThat(checkResult.getResultType()).isEqualTo(StartUpCheck.ResultType.GOOD);
    }

    @Test
    void startingShouldFailWhenTurningOffDeduplication() {
        testee.validate(() -> DEDUPLICATION);

        StartUpCheck.CheckResult checkResult = testee.validate(() -> PASSTHROUGH);

        assertThat(checkResult.getResultType()).isEqualTo(StartUpCheck.ResultType.BAD);
    }

    @Test
    void validatingSeveralTimeTheSameStrategyShouldNotAddEventsToTheHistory() {
        testee.validate(() -> DEDUPLICATION);
        testee.validate(() -> DEDUPLICATION);

        History history = Mono.from(eventStore.getEventsOfAggregate(RegisterStorageStrategyCommandHandler.AGGREGATE_ID)).block();

        assertThat(history.getEventsJava()).hasSize(1);
    }
}