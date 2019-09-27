/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.task.eventsourcing.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.apache.james.task.eventsourcing.TerminationSubscriberContract;

import com.github.steveash.guavate.Guavate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

class RabbitMQTerminationSubscriberTest implements TerminationSubscriberContract {
    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer();
    private static final Set<EventDTOModule<?, ?>> MODULES = TasksSerializationModule.MODULES.apply(TASK_SERIALIZER).stream().collect(Guavate.toImmutableSet());
    private static final JsonEventSerializer SERIALIZER = new JsonEventSerializer(MODULES);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    @Override
    public TerminationSubscriber subscriber() {
        RabbitMQTerminationSubscriber subscriber = new RabbitMQTerminationSubscriber(rabbitMQExtension.getRabbitConnectionPool(), SERIALIZER);
        subscriber.start();
        return subscriber;
    }

    @Test
    void givenTwoTerminationSubscribersWhenAnEventIsSentItShouldBeReceivedByBoth() {
        TerminationSubscriber subscriber1 = subscriber();
        TerminationSubscriber subscriber2 = subscriber();

        sendEvents(subscriber1, COMPLETED_EVENT);

        List<List<Event>> listenedEvents = Flux.just(subscriber1, subscriber2)
            .subscribeOn(Schedulers.elastic())
            .flatMap(this::collectEvents)
            .collectList()
            .block();
        assertThat(listenedEvents).hasSize(2);
        assertThat(listenedEvents.get(0)).containsExactly(COMPLETED_EVENT);
        assertThat(listenedEvents.get(1)).containsExactly(COMPLETED_EVENT);
    }
}