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

package org.apache.james.backends.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.TEN_SECONDS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.rabbitmq.ChannelPool;

class ReactorRabbitMQChannelPoolTest implements ChannelPoolContract {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private List<ReactorRabbitMQChannelPool> channelPools;

    @BeforeEach
    void beforeEach() {
        channelPools = new ArrayList<>();
    }

    @AfterEach
    void afterEach() {
        channelPools.forEach(ReactorRabbitMQChannelPool::close);
    }

    @Override
    public ChannelPool getChannelPool(int poolSize) {
        ReactorRabbitMQChannelPool channelPool = generateChannelPool(poolSize);
        channelPools.add(channelPool);

        return channelPool;
    }

    private ReactorRabbitMQChannelPool generateChannelPool(int poolSize) {
        ReactorRabbitMQChannelPool reactorRabbitMQChannelPool = new ReactorRabbitMQChannelPool(
            rabbitMQExtension.getConnectionPool().getResilientConnection(),
            ReactorRabbitMQChannelPool.Configuration.builder()
                .retries(2)
                .maxBorrowDelay(Duration.ofSeconds(2))
                .maxChannel(poolSize),
            new RecordingMetricFactory());
        reactorRabbitMQChannelPool.start();
        return reactorRabbitMQChannelPool;
    }

    // Pool wait timeout is an expected exception
    @Test
    void concurrentRequestOnChannelMonoLeadToPoolWaitTimeoutException() {
        Mono<? extends Channel> channelMono = getChannelPool(99).getChannelMono();

        ConcurrentLinkedQueue<Channel> listChannels = new ConcurrentLinkedQueue<>();
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> listChannels.add(channelMono.block()))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(Duration.ofSeconds(30)))
            .isInstanceOf(ExecutionException.class)
            .hasMessageContaining("java.util.NoSuchElementException: Timeout waiting for idle object");
    }

    @Test
    void usedChannelShouldBeClosedWhenPoolIsClosed() throws Exception {
        ChannelPool channelPool = generateChannelPool(2);
        Channel channel = channelPool.getChannelMono().block();
        assertThat(channel.isOpen()).isTrue();
        channelPool.close();

        Thread.sleep(100); // Release of channels is done asynchronously

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void notUsedChannelShouldBeClosedWhenPoolIsClosed() {
        ChannelPool channelPool = generateChannelPool(2);
        Channel channel = channelPool.getChannelMono().block();
        assertThat(channel.isOpen()).isTrue();
        channelPool.getChannelCloseHandler().accept(SignalType.ON_NEXT, channel);
        channelPool.close();

        Awaitility.await().atMost(TEN_SECONDS)
            .untilAsserted(() -> assertThat(channel.isOpen()).isFalse());
    }

    @Test
    void channelBorrowShouldNotThrowWhenClosedChannel() throws Exception {
        ChannelPool channelPool = generateChannelPool(1);
        Channel channel = channelPool.getChannelMono().block();
        returnToThePool(channelPool, channel);

        // unexpected closing, connection timeout, rabbitmq temporary down...
        channel.close();

        assertThat(channelPool.getChannelMono()
                .block()
                .isOpen())
            .isTrue();
    }
}
