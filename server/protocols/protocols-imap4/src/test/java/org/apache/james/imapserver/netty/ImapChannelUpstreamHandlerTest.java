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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class ImapChannelUpstreamHandlerTest {
    private static final ImapMessage FAILING_COMMAND = new ImapMessage() { };
    private static final ImapMessage LONG_COMMAND = new ImapMessage() { };

    @Test
    void cleanupOfACompletedRequestShouldNotClearTheInFlightHandleOfTheNextOne() {
        Sinks.One<Void> failingCommandResult = Sinks.one();
        ImapProcessor processor = new ImapProcessor() {
            @Override
            public Mono<Void> processReactive(ImapMessage message, Responder responder, ImapSession session) {
                if (message == FAILING_COMMAND) {
                    return failingCommandResult.asMono();
                }
                return Mono.never();
            }

            @Override
            public void configure(ImapConfiguration imapConfiguration) {
            }
        };
        ImapChannelUpstreamHandler handler = ImapChannelUpstreamHandler.builder()
            .hello("* OK test")
            .processor(processor)
            .encoder((message, composer) -> { })
            .compress(false)
            .imapMetrics(new ImapMetrics(new RecordingMetricFactory()))
            .ignoreIDLEUponProcessing(false)
            .heartbeatInterval(Duration.ZERO)
            .reactiveThrottler(new ReactiveThrottler(new NoopGaugeRegistry(), -1, 0))
            .connectionChecks(ImmutableSet.of())
            .proxyRequired(false)
            .imapChannelGroup(new DefaultChannelGroup(GlobalEventExecutor.INSTANCE))
            .build();
        // The failure of the first request is propagated once its slot is released: the next request is read then
        AtomicBoolean nextRequestRead = new AtomicBoolean(false);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(NettyConstants.CORE_HANDLER, handler);
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        if (!nextRequestRead.getAndSet(true)) {
                            ctx.pipeline().fireChannelRead(LONG_COMMAND);
                        }
                    }
                });
            }
        }) {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress("10.1.2.3", 40000);
            }
        };
        channel.runPendingTasks();

        channel.pipeline().fireChannelRead(FAILING_COMMAND);
        failingCommandResult.tryEmitError(new RuntimeException("failing command"));
        channel.runPendingTasks();

        assertThat(nextRequestRead).isTrue();
        assertThat(channel.attr(NettyConstants.REQUEST_IN_FLIGHT_ATTRIBUTE_KEY).get())
            .as("the long command can still be cancelled")
            .isNotNull();
        assertThat(channel.pipeline().get(NettyConstants.HEARTBEAT_HANDLER))
            .as("the long command has its heartbeat")
            .isNotNull();
    }
}
