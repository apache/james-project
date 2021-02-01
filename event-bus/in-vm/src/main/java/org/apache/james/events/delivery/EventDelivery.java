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

package org.apache.james.events.delivery;

import static org.apache.james.events.delivery.EventDelivery.PermanentFailureHandler.NO_HANDLER;
import static org.apache.james.events.delivery.EventDelivery.Retryer.NO_RETRYER;

import org.apache.james.events.Event;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.RetryBackoffConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public interface EventDelivery {

    class DeliveryOption {
        public static DeliveryOption of(Retryer retrier, PermanentFailureHandler permanentFailureHandler) {
            return new DeliveryOption(retrier, permanentFailureHandler);
        }

        public static DeliveryOption none() {
            return new DeliveryOption(NO_RETRYER, NO_HANDLER);
        }

        private final Retryer retrier;
        private final PermanentFailureHandler permanentFailureHandler;

        private DeliveryOption(Retryer retrier, PermanentFailureHandler permanentFailureHandler) {
            this.retrier = retrier;
            this.permanentFailureHandler = permanentFailureHandler;
        }

        Retryer getRetrier() {
            return retrier;
        }

        PermanentFailureHandler getPermanentFailureHandler() {
            return permanentFailureHandler;
        }
    }


    interface Retryer {

        Retryer NO_RETRYER = (executionResult, event) -> executionResult;

        class BackoffRetryer implements Retryer {

            public static BackoffRetryer of(RetryBackoffConfiguration retryBackoff, EventListener listener) {
                return new BackoffRetryer(retryBackoff, listener);
            }

            private static final Logger LOGGER = LoggerFactory.getLogger(BackoffRetryer.class);

            private final RetryBackoffConfiguration retryBackoff;
            private final EventListener listener;

            public BackoffRetryer(RetryBackoffConfiguration retryBackoff, EventListener listener) {
                this.retryBackoff = retryBackoff;
                this.listener = listener;
            }

            @Override
            public Mono<Void> doRetry(Mono<Void> executionResult, Event event) {
                return executionResult
                    .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.elastic()))
                    .doOnError(throwable -> LOGGER.error("listener {} exceeded maximum retry({}) to handle event {}",
                        listener.getClass().getCanonicalName(),
                        retryBackoff.getMaxRetries(),
                        event.getClass().getCanonicalName(),
                        throwable))
                    .then();
            }
        }

        Mono<Void> doRetry(Mono<Void> executionResult, Event event);
    }

    interface PermanentFailureHandler {

        PermanentFailureHandler NO_HANDLER = event -> Mono.error(new UnsupportedOperationException("doesn't handle error"));

        class StoreToDeadLetters implements PermanentFailureHandler {

            public static StoreToDeadLetters of(Group group, EventDeadLetters eventDeadLetters) {
                return new StoreToDeadLetters(group, eventDeadLetters);
            }

            private final Group group;
            private final EventDeadLetters eventDeadLetters;

            private StoreToDeadLetters(Group group, EventDeadLetters eventDeadLetters) {
                this.group = group;
                this.eventDeadLetters = eventDeadLetters;
            }

            @Override
            public Mono<Void> handle(Event event) {
                return eventDeadLetters.store(group, event).then();
            }
        }

        Mono<Void> handle(Event event);
    }

    Mono<Void> deliver(EventListener.ReactiveEventListener listener, Event event, DeliveryOption option);

    default Mono<Void> deliver(EventListener listener, Event event, DeliveryOption option) {
        return deliver(EventListener.wrapReactive(listener), event, option);
    }
}
