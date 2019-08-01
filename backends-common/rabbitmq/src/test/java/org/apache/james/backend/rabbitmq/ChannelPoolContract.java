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

package org.apache.james.backend.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.rabbitmq.ChannelPool;

interface ChannelPoolContract {

    ChannelPool getChannelPool(int poolSize);

    @Test
    default void channelPoolShouldCreateDifferentChannels() {
        ChannelPool channelPool = getChannelPool(2);
        Channel channel1 = borrowChannel(channelPool);
        Channel channel2 = borrowChannel(channelPool);

        assertThat(channel1.getChannelNumber())
            .isNotEqualTo(channel2.getChannelNumber());
    }

    @Test
    default void channelPoolShouldReleaseChannelWhenReturnedToThePool() {
        ChannelPool channelPool = getChannelPool(2);
        channelPool.getChannelMono();
        borrowChannel(channelPool);
        Channel channel2 = borrowChannel(channelPool);

        returnToThePool(channelPool, channel2);
        Channel channelAfterReturned = borrowChannel(channelPool);

        assertThat(channelAfterReturned.getChannelNumber())
            .isEqualTo(channel2.getChannelNumber());
    }

    @Test
    default void channelPoolShouldWaitTillTheNextReleaseWhenAllChannelsAreTaken() {
        ChannelPool channelPool = getChannelPool(2);
        channelPool.getChannelMono();
        borrowChannel(channelPool);
        Channel channel2 = borrowChannel(channelPool);

        Mono.delay(Duration.ofSeconds(2))
            .doOnSuccess(any -> returnToThePool(channelPool, channel2))
            .subscribe();

        Channel channelAfterReturned = borrowChannel(channelPool);
        assertThat(channelAfterReturned.getChannelNumber())
            .isEqualTo(channel2.getChannelNumber());
    }

    @Test
    default void channelPoolShouldThrowAfterTimeoutWhenAllChannelsAreTaken() {
        ChannelPool channelPool = getChannelPool(2);
        Mono<? extends Channel> channelMono = channelPool.getChannelMono();
        borrowChannel(channelPool);
        borrowChannel(channelPool);

        assertThatThrownBy(channelMono::block)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Timeout waiting for idle object");
    }

    default Channel borrowChannel(ChannelPool channelPool) {
        Mono<? extends Channel> channelMono = channelPool.getChannelMono();
        return channelMono.block();
    }

    default void returnToThePool(ChannelPool channelPool, Channel channel) {
        channelPool.getChannelCloseHandler()
            .accept(SignalType.ON_COMPLETE, channel);
    }
}
