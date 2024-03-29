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

import java.util.Optional;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Abstract base class for {@link ChannelInitializer} implementations
 */
@ChannelHandler.Sharable
public abstract class AbstractChannelPipelineFactory<C extends SocketChannel> extends ChannelInitializer<C> {
    public static final int MAX_LINE_LENGTH = 8192;

    private final int timeout;
    private final boolean proxyRequired;
    private final Optional<ConnectionLimitUpstreamHandler> connectionLimitUpstreamHandler;
    private final Optional<ConnectionPerIpLimitUpstreamHandler> connectionPerIpLimitUpstreamHandler;
    private final ChannelHandlerFactory frameHandlerFactory;
    private final EventExecutorGroup eventExecutorGroup;

    public AbstractChannelPipelineFactory(ChannelHandlerFactory frameHandlerFactory, EventExecutorGroup eventExecutorGroup) {
        this(0, 0, 0, false, frameHandlerFactory, eventExecutorGroup);
    }

    public AbstractChannelPipelineFactory(int timeout, int maxConnections, int maxConnectsPerIp, boolean proxyRequired,
                                          ChannelHandlerFactory frameHandlerFactory, EventExecutorGroup eventExecutorGroup) {
        this.timeout = timeout;
        this.proxyRequired = proxyRequired;
        this.frameHandlerFactory = frameHandlerFactory;
        this.eventExecutorGroup = eventExecutorGroup;
        this.connectionLimitUpstreamHandler = ConnectionLimitUpstreamHandler.forCount(maxConnections);
        this.connectionPerIpLimitUpstreamHandler = ConnectionPerIpLimitUpstreamHandler.forCount(maxConnectsPerIp);
    }
    
    
    @Override
    protected void initChannel(C channel) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = channel.pipeline();

        connectionLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_HANDLER, handler));
        connectionPerIpLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_PER_IP_HANDLER, handler));

        if (proxyRequired) {
            pipeline.addLast(HandlerConstants.PROXY_HANDLER, new HAProxyMessageDecoder());
        }

        // Add the ChunkedWriteHandler to be able to write ChunkInput
        pipeline.addLast(HandlerConstants.CHUNK_HANDLER, new ChunkedWriteHandler());
        pipeline.addLast(HandlerConstants.TIMEOUT_HANDLER, new TimeoutHandler(timeout));
        // Add the text line decoder which limit the max line length, don't strip the delimiter and use CRLF as delimiter
        pipeline.addLast(eventExecutorGroup, HandlerConstants.FRAMER, frameHandlerFactory.create(pipeline));
        pipeline.addLast(eventExecutorGroup, HandlerConstants.CORE_HANDLER, createHandler());
    }

    
    /**
     * Create the core {@link ChannelInboundHandlerAdapter} to use
     *
     * @return coreHandler
     */
    protected abstract ChannelInboundHandlerAdapter createHandler();

}
