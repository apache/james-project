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

import java.util.Objects;

import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Listens to events.<br>
 */
public interface EventListener {

    interface ReactiveEventListener extends EventListener {
        Publisher<Void> reactiveEvent(Event event);

        default void event(Event event) throws Exception {
            Mono.from(reactiveEvent(event))
                .subscribeOn(Schedulers.elastic())
                .block();
        }
    }

    interface GroupEventListener extends EventListener {
        Group getDefaultGroup();
    }

    interface ReactiveGroupEventListener extends ReactiveEventListener, GroupEventListener {
        default void event(Event event) throws Exception {
            Mono.from(reactiveEvent(event))
                .subscribeOn(Schedulers.elastic())
                .block();
        }
    }

    class ReactiveWrapper<T extends EventListener> implements ReactiveEventListener {
        protected final T delegate;

        private ReactiveWrapper(T delegate) {
            this.delegate = delegate;
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            return Mono.fromRunnable(Throwing.runnable(() -> delegate.event(event)))
                .subscribeOn(Schedulers.elastic())
                .then();
        }

        @Override
        public void event(Event event) throws Exception {
            delegate.event(event);
        }

        @Override
        public ExecutionMode getExecutionMode() {
            return delegate.getExecutionMode();
        }

        @Override
        public boolean isHandling(Event event) {
            return delegate.isHandling(event);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof ReactiveWrapper) {
                ReactiveWrapper<?> that = (ReactiveWrapper<?>) o;

                return Objects.equals(this.delegate, that.delegate);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(delegate);
        }
    }

    class ReactiveGroupWrapper extends ReactiveWrapper<GroupEventListener> implements GroupEventListener, ReactiveGroupEventListener {
        private ReactiveGroupWrapper(GroupEventListener delegate) {
            super(delegate);
        }

        @Override
        public Group getDefaultGroup() {
            return delegate.getDefaultGroup();
        }
    }

    enum ExecutionMode {
        SYNCHRONOUS,
        ASYNCHRONOUS
    }

    static ReactiveEventListener wrapReactive(EventListener listener) {
        return new ReactiveWrapper<>(listener);
    }

    static ReactiveGroupEventListener wrapReactive(GroupEventListener groupEventListener) {
        return new ReactiveGroupWrapper(groupEventListener);
    }

    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }


    default boolean isHandling(Event event) {
        return true;
    }

    /**
     * Informs this listener about the given event.
     *
     * @param event not null
     */
    void event(Event event) throws Exception;
}
