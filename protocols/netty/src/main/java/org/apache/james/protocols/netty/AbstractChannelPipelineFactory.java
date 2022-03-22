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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * Abstract base class for {@link ChannelInitializer} implementations
 */
@ChannelHandler.Sharable
public abstract class AbstractChannelPipelineFactory extends ChannelInitializer<SocketChannel> {
    public static final int MAX_LINE_LENGTH = 8192;

    protected final ConnectionLimitInboundHandler connectionLimitHandler;
    protected final ConnectionPerIpLimitInboundHandler connectionPerIpLimitHandler;
    private final int readTimeout;
    private final ChannelHandlerFactory frameHandlerFactory;
    protected final EventExecutorGroup executorGroup;

    public AbstractChannelPipelineFactory(ChannelHandlerFactory frameHandlerFactory, EventLoopGroupManager groupManager) {
        this(0, 0, 0, frameHandlerFactory, groupManager);
    }

    public AbstractChannelPipelineFactory(int readTimeout, int maxConnections, int maxConnectsPerIp,
                                          ChannelHandlerFactory frameHandlerFactory, EventLoopGroupManager groupManager) {
        this.connectionLimitHandler = maxConnections > 0 ? new ConnectionLimitInboundHandler(maxConnections) : null;
        this.connectionPerIpLimitHandler = maxConnectsPerIp > 0 ? new ConnectionPerIpLimitInboundHandler(maxConnectsPerIp) : null;
        this.readTimeout = readTimeout;
        this.frameHandlerFactory = frameHandlerFactory;
        this.executorGroup = groupManager.getExecutorGroup();
    }

    @Override
    protected final void initChannel(SocketChannel channel) throws Exception {
        initPipeline(channel.pipeline());
    }

    protected void initPipeline(ChannelPipeline pipeline) throws Exception {
        // Create a default pipeline implementation.
        addLastIfNotNull(pipeline, HandlerConstants.CONNECTION_LIMIT_HANDLER, connectionLimitHandler);

        addLastIfNotNull(pipeline, HandlerConstants.CONNECTION_PER_IP_LIMIT_HANDLER, connectionPerIpLimitHandler);

        // Add the text line decoder which limit the max line length, don't strip the delimiter and use CRLF as delimiter
        pipeline.addLast(HandlerConstants.FRAMER, frameHandlerFactory.create(pipeline));

        // Add the ChunkedWriteHandler to be able to write ChunkInput
        pipeline.addLast(HandlerConstants.CHUNK_HANDLER, new ChunkedWriteHandler());

        int readTimeout = this.readTimeout;
        if (readTimeout > 0) {
            pipeline.addLast(HandlerConstants.READ_TIMEOUT_HANDLER, new ReadTimeoutHandler(readTimeout));
        }

        pipeline.addLast(executorGroup, HandlerConstants.CORE_HANDLER, createHandler());
    }

    private static void addLastIfNotNull(ChannelPipeline pipeline, String name, ChannelHandler handler) {
        if (handler != null) {
            pipeline.addLast(name, handler);
        }
    }

    /**
     * Create the core {@link ChannelInboundHandlerAdapter} to use
     *
     * @return coreHandler
     */
    protected abstract ChannelInboundHandlerAdapter createHandler();

}
