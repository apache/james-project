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

import javax.net.ssl.SSLEngine;

import org.apache.james.protocols.api.Encryption;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.EventExecutorGroup;


/**
 * Abstract base class for {@link ChannelPipeline} implementations which use TLS
 */
@ChannelHandler.Sharable
public abstract class AbstractSSLAwareChannelPipelineFactory<C extends SocketChannel> extends AbstractChannelPipelineFactory<C> {

    private Encryption secure;

    public AbstractSSLAwareChannelPipelineFactory(int timeout,
                                                  int maxConnections, int maxConnectsPerIp, ChannelGroup group,
                                                  ChannelHandlerFactory frameHandlerFactory,
                                                  EventExecutorGroup eventExecutorGroup) {
        super(timeout, maxConnections, maxConnectsPerIp, group, frameHandlerFactory, eventExecutorGroup);
    }

    public AbstractSSLAwareChannelPipelineFactory(int timeout,
            int maxConnections, int maxConnectsPerIp, ChannelGroup group, Encryption secure,
            ChannelHandlerFactory frameHandlerFactory, EventExecutorGroup eventExecutorGroup) {
        this(timeout, maxConnections, maxConnectsPerIp, group, frameHandlerFactory, eventExecutorGroup);

        this.secure = secure;
    }

    @Override
    public void initChannel(C channel) throws Exception {
        super.initChannel(channel);

        ChannelPipeline pipeline = channel.pipeline();

        if (isSSLSocket()) {
            // We need to set clientMode to false.
            // See https://issues.apache.org/jira/browse/JAMES-1025
            SSLEngine engine = secure.createSSLEngine();
            engine.setUseClientMode(false);
            pipeline.addFirst(HandlerConstants.SSL_HANDLER, new SslHandler(engine));
        }
    }

    /**
     * Return if the socket is using SSL/TLS
     */
    protected boolean isSSLSocket() {
        return secure != null && secure.getContext() != null && !secure.isStartTLS();
    }
}
