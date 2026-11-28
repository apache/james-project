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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

class KeyRegistrationHandlerTest {
    private static final RegistrationKey KEY = new EventBusTestFixture.TestRegistrationKey("a");
    private static final EventListener.ReactiveEventListener LISTENER = EventListener.wrapReactive(event -> { });

    private Sender sender;
    private LocalListenerRegistry localListenerRegistry;
    private KeyRegistrationHandler testee;

    @BeforeEach
    void setUp() {
        sender = mock(Sender.class);
        when(sender.unbind(any())).thenReturn(Mono.empty());
        localListenerRegistry = new LocalListenerRegistry();
        RetryBackoffConfiguration noRetry = RetryBackoffConfiguration.builder()
            .maxRetries(0)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0)
            .build();
        testee = new KeyRegistrationHandler(new DefaultNamingStrategy(new EventBusName("test")), EventBusId.random(), null,
            sender, null, null, localListenerRegistry, null, new RabbitMQEventBus.Configurations(null, noRetry), new RecordingMetricFactory());
    }

    @Test
    void registerCancelledDuringBindShouldRemoveTheListener() {
        when(sender.bind(any())).thenReturn(Mono.never());

        Disposable subscription = testee.register(LISTENER, KEY).subscribe();
        assertThat(localListenerRegistry.getLocalListeners(KEY)).containsOnly(LISTENER);
        subscription.dispose();

        assertThat(localListenerRegistry.getLocalListeners(KEY)).isEmpty();
    }

    @Test
    void registerShouldRemoveTheListenerWhenTheBindFails() {
        when(sender.bind(any())).thenReturn(Mono.error(new RuntimeException("broker unavailable")));

        assertThatThrownBy(() -> testee.register(LISTENER, KEY).block())
            .isInstanceOf(RuntimeException.class);

        assertThat(localListenerRegistry.getLocalListeners(KEY)).isEmpty();
    }
}
