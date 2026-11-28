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

package org.apache.james.task.eventsourcing;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.task.Hostname;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import scala.Option;
import scala.collection.immutable.List;

/**
 * Delegates to a projection and handles an event right after the next load, to reproduce an event handled
 * while another one is being applied.
 */
class LoadHookedTaskExecutionDetailsProjection implements TaskExecutionDetailsProjection {
    static final Hostname HOSTNAME = new Hostname("foo");

    private final TaskExecutionDetailsProjection delegate;
    private final AtomicReference<Event> eventToHandleAfterNextLoad = new AtomicReference<>();

    LoadHookedTaskExecutionDetailsProjection(TaskExecutionDetailsProjection delegate) {
        this.delegate = delegate;
    }

    void handleAfterNextLoad(Event event) {
        eventToHandleAfterNextLoad.set(event);
    }

    void handle(Event event) {
        Mono.from(handleReactive(event)).block();
    }

    private Publisher<Void> handleReactive(Event event) {
        return asSubscriber(HOSTNAME).handleReactive(new EventWithState(event, Option.empty()));
    }

    @Override
    public Publisher<TaskExecutionDetails> loadReactive(TaskId taskId) {
        return Mono.from(delegate.loadReactive(taskId))
            .flatMap(loaded -> Mono.justOrEmpty(eventToHandleAfterNextLoad.getAndSet(null))
                .flatMap(event -> Mono.from(handleReactive(event)))
                .thenReturn(loaded));
    }

    @Override
    public Option<TaskExecutionDetails> load(TaskId taskId) {
        return delegate.load(taskId);
    }

    @Override
    public List<TaskExecutionDetails> list() {
        return delegate.list();
    }

    @Override
    public void update(TaskExecutionDetails details) {
        delegate.update(details);
    }

    @Override
    public Publisher<TaskExecutionDetails> listReactive() {
        return delegate.listReactive();
    }

    @Override
    public Publisher<TaskExecutionDetails> listDetailsByBeforeDate(Instant beforeDate) {
        return delegate.listDetailsByBeforeDate(beforeDate);
    }

    @Override
    public Publisher<Void> updateReactive(TaskExecutionDetails details) {
        return delegate.updateReactive(details);
    }

    @Override
    public Publisher<Boolean> updateReactive(TaskExecutionDetails details, TaskManager.Status expectedStatus) {
        return delegate.updateReactive(details, expectedStatus);
    }

    @Override
    public Publisher<Boolean> updateAdditionalInformationReactive(TaskExecutionDetails details, TaskManager.Status expectedStatus) {
        return delegate.updateAdditionalInformationReactive(details, expectedStatus);
    }

    @Override
    public Publisher<Void> remove(TaskExecutionDetails taskExecutionDetails) {
        return delegate.remove(taskExecutionDetails);
    }
}
