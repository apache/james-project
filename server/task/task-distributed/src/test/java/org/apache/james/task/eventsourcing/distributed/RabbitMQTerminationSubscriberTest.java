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
import static org.awaitility.Duration.ONE_MINUTE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.apache.james.task.eventsourcing.TerminationSubscriberContract;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Flux;

class RabbitMQTerminationSubscriberTest implements TerminationSubscriberContract {
    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer();
    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = new JsonTaskAdditionalInformationSerializer();
    private static final Set<EventDTOModule<?, ?>> MODULES = TasksSerializationModule.list(TASK_SERIALIZER, JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER).stream().collect(Guavate.toImmutableSet());
    private static final JsonEventSerializer SERIALIZER = new JsonEventSerializer(MODULES);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    @Override
    public TerminationSubscriber subscriber() {
        RabbitMQTerminationSubscriber subscriber = new RabbitMQTerminationSubscriber(rabbitMQExtension.getRabbitChannelPool(), SERIALIZER);
        subscriber.start();
        return subscriber;
    }

    @Test
    void givenTwoTerminationSubscribersWhenAnEventIsSentItShouldBeReceivedByBoth() {
        TerminationSubscriber subscriber1 = subscriber();
        TerminationSubscriber subscriber2 = subscriber();

        Flux<Event> firstListener = Flux.from(subscriber1.listenEvents());
        Flux<Event> secondListener = Flux.from(subscriber2.listenEvents());

        sendEvents(subscriber1, COMPLETED_EVENT);

        List<Event> receivedEventsFirst = new ArrayList<>();
        firstListener.subscribe(receivedEventsFirst::add);
        List<Event> receivedEventsSecond = new ArrayList<>();
        secondListener.subscribe(receivedEventsSecond::add);

        Awaitility.await().atMost(ONE_MINUTE).until(() -> receivedEventsFirst.size() == 1 && receivedEventsSecond.size() == 1);

        assertThat(receivedEventsFirst).containsExactly(COMPLETED_EVENT);
        assertThat(receivedEventsSecond).containsExactly(COMPLETED_EVENT);
    }
}