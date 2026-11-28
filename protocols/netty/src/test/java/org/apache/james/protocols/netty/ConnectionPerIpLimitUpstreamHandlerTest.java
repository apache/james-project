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

package org.apache.james.protocols.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.netty.channel.embedded.EmbeddedChannel;

class ConnectionPerIpLimitUpstreamHandlerTest {
    private static final InetSocketAddress REMOTE = new InetSocketAddress("10.1.2.3", 40000);
    private static final int CONNECTION_COUNT = 4;
    private static final int ROUNDS = 2000;

    private static class RemoteChannel extends EmbeddedChannel {
        @Override
        protected SocketAddress remoteAddress0() {
            return REMOTE;
        }
    }

    @Test
    void limitShouldApplyToTheFirstConnectionsOpenedAtOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONNECTION_COUNT);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                ConnectionPerIpLimitUpstreamHandler handler = ConnectionPerIpLimitUpstreamHandler.forCount(1).get();
                List<RemoteChannel> channels = IntStream.range(0, CONNECTION_COUNT)
                    .mapToObj(i -> new RemoteChannel())
                    .peek(channel -> channel.pipeline().addLast(handler))
                    .collect(ImmutableList.toImmutableList());
                CyclicBarrier barrier = new CyclicBarrier(CONNECTION_COUNT);

                List<Future<?>> activations = channels.stream()
                    .<Future<?>>map(channel -> executor.submit(() -> {
                        barrier.await();
                        channel.pipeline().fireChannelActive();
                        return null;
                    }))
                    .collect(ImmutableList.toImmutableList());
                for (Future<?> activation : activations) {
                    activation.get(10, TimeUnit.SECONDS);
                }

                assertThat(channels.stream().filter(RemoteChannel::isOpen).count())
                    .describedAs("open connections in round %d", round)
                    .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
