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

package org.apache.james.events;

import static org.apache.james.events.PostgresEventDeadLettersModule.PostgresEventDeadLettersTable.EVENT;
import static org.apache.james.events.PostgresEventDeadLettersModule.PostgresEventDeadLettersTable.GROUP;
import static org.apache.james.events.PostgresEventDeadLettersModule.PostgresEventDeadLettersTable.INSERTION_ID;
import static org.apache.james.events.PostgresEventDeadLettersModule.PostgresEventDeadLettersTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.jooq.Record;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEventDeadLetters implements EventDeadLetters {
    private final PostgresExecutor postgresExecutor;
    private final EventSerializer eventSerializer;

    @Inject
    public PostgresEventDeadLetters(PostgresExecutor postgresExecutor, EventSerializer eventSerializer) {
        this.postgresExecutor = postgresExecutor;
        this.eventSerializer = eventSerializer;
    }

    @Override
    public Mono<InsertionId> store(Group registeredGroup, Event failDeliveredEvent) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredEvent != null, FAIL_DELIVERED_EVENT_CANNOT_BE_NULL);

        InsertionId insertionId = InsertionId.random();
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(INSERTION_ID, insertionId.getId())
                .set(GROUP, registeredGroup.asString())
                .set(EVENT, eventSerializer.toJson(failDeliveredEvent))))
            .thenReturn(insertionId);
    }

    @Override
    public Mono<Void> remove(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(INSERTION_ID.eq(failDeliveredInsertionId.getId()))));
    }

    @Override
    public Mono<Void> remove(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(GROUP.eq(registeredGroup.asString()))));
    }

    @Override
    public Mono<Event> failedEvent(Group registeredGroup, InsertionId failDeliveredInsertionId) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);
        Preconditions.checkArgument(failDeliveredInsertionId != null, FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL);

        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(EVENT)
            .from(TABLE_NAME)
            .where(INSERTION_ID.eq(failDeliveredInsertionId.getId()))))
            .map(this::deserializeEvent);
    }

    private Event deserializeEvent(Record record) {
        return eventSerializer.asEvent(record.get(EVENT));
    }

    @Override
    public Flux<InsertionId> failedIds(Group registeredGroup) {
        Preconditions.checkArgument(registeredGroup != null, REGISTERED_GROUP_CANNOT_BE_NULL);

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext
            .select(INSERTION_ID)
            .from(TABLE_NAME)
            .where(GROUP.eq(registeredGroup.asString()))))
            .map(record -> InsertionId.of(record.get(INSERTION_ID)));
    }

    @Override
    public Flux<Group> groupsWithFailedEvents() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext
            .selectDistinct(GROUP)
            .from(TABLE_NAME)))
            .map(Throwing.function(record -> Group.deserialize(record.get(GROUP))));
    }

    @Override
    public Mono<Boolean> containEvents() {
        return postgresExecutor.executeExists(dslContext -> dslContext.selectOne()
            .from(TABLE_NAME)
            .where());
    }
}
