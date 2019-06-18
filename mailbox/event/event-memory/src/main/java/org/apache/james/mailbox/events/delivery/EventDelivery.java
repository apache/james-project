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

package org.apache.james.mailbox.events.delivery;

import static org.apache.james.mailbox.events.delivery.EventDelivery.PermanentFailureHandler.NO_HANDLER;
import static org.apache.james.mailbox.events.delivery.EventDelivery.Retryer.NO_RETRYER;

import java.time.Duration;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

        class BackoffRetryer implements EventDelivery.Retryer {

            public static BackoffRetryer of(RetryBackoffConfiguration retryBackoff, MailboxListener mailboxListener) {
                return new BackoffRetryer(retryBackoff, mailboxListener);
            }

            private static final Logger LOGGER = LoggerFactory.getLogger(BackoffRetryer.class);
            private static final Duration MAX_BACKOFF = Duration.ofMillis(Long.MAX_VALUE);

            private final RetryBackoffConfiguration retryBackoff;
            private final MailboxListener mailboxListener;

            public BackoffRetryer(RetryBackoffConfiguration retryBackoff, MailboxListener mailboxListener) {
                this.retryBackoff = retryBackoff;
                this.mailboxListener = mailboxListener;
            }

            @Override
            public Mono<Void> doRetry(Mono<Void> executionResult, Event event) {
                return executionResult
                    .retryBackoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff(), MAX_BACKOFF, retryBackoff.getJitterFactor())
                    .doOnError(throwable -> LOGGER.error("listener {} exceeded maximum retry({}) to handle event {}",
                        mailboxListener.getClass().getCanonicalName(),
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

        class StoreToDeadLetters implements EventDelivery.PermanentFailureHandler {

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
                return eventDeadLetters.store(group, event, EventDeadLetters.InsertionId.random());
            }
        }

        Mono<Void> handle(Event event);
    }

    class ExecutionStages {

        public static ExecutionStages empty() {
            return new ExecutionStages(Mono.empty(), Mono.empty());
        }

        static ExecutionStages synchronous(Mono<Void> synchronousListenerFuture) {
            return new ExecutionStages(synchronousListenerFuture, Mono.empty());
        }

        static ExecutionStages asynchronous(Mono<Void> asynchronousListenerFuture) {
            return new ExecutionStages(Mono.empty(),asynchronousListenerFuture);
        }

        private final Mono<Void> synchronousListenerFuture;
        private final Mono<Void> asynchronousListenerFuture;

        private ExecutionStages(Mono<Void> synchronousListenerFuture, Mono<Void> asynchronousListenerFuture) {
            this.synchronousListenerFuture = synchronousListenerFuture;
            this.asynchronousListenerFuture = asynchronousListenerFuture;
        }

        public Mono<Void> synchronousListenerFuture() {
            return synchronousListenerFuture;
        }

        public Mono<Void> allListenerFuture() {
            return synchronousListenerFuture
                .concatWith(asynchronousListenerFuture)
                .then();
        }

        public ExecutionStages combine(ExecutionStages another) {
            return new ExecutionStages(
                Flux.concat(this.synchronousListenerFuture, another.synchronousListenerFuture).then(),
                Flux.concat(this.asynchronousListenerFuture, another.asynchronousListenerFuture).then());
        }
    }

    ExecutionStages deliver(MailboxListener listener, Event event, DeliveryOption option);
}
