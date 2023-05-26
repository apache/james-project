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

import java.util.function.Supplier;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.EventExecutorGroup;


/**
 * Abstract base class for {@link ChannelPipeline} implementations which use TLS
 */
@ChannelHandler.Sharable
public abstract class AbstractSSLAwareChannelPipelineFactory<C extends SocketChannel> extends AbstractChannelPipelineFactory<C> {
    private final boolean proxyRequired;
    private Supplier<Encryption> secure;

    public AbstractSSLAwareChannelPipelineFactory(int timeout,
                                                  int maxConnections, int maxConnectsPerIp,
                                                  boolean proxyRequired,
                                                  ChannelHandlerFactory frameHandlerFactory,
                                                  EventExecutorGroup eventExecutorGroup) {
        super(timeout, maxConnections, maxConnectsPerIp, proxyRequired, frameHandlerFactory, eventExecutorGroup);
        this.proxyRequired = proxyRequired;
    }

    public AbstractSSLAwareChannelPipelineFactory(int timeout,
            int maxConnections, int maxConnectsPerIp, boolean proxyRequired, Supplier<Encryption> secure,
            ChannelHandlerFactory frameHandlerFactory, EventExecutorGroup eventExecutorGroup) {
        this(timeout, maxConnections, maxConnectsPerIp, proxyRequired, frameHandlerFactory, eventExecutorGroup);

        this.secure = secure;
    }

    @Override
    public void initChannel(C channel) throws Exception {
        super.initChannel(channel);

        if (isSSLSocket()) {
            if (proxyRequired) {
                channel.pipeline().addAfter("proxyInformationHandler", HandlerConstants.SSL_HANDLER, secure.get().sslHandler());
            } else {
                channel.pipeline().addFirst(HandlerConstants.SSL_HANDLER, secure.get().sslHandler());
            }
        }
    }

    /**
     * Return if the socket is using SSL/TLS
     */
    protected boolean isSSLSocket() {
        Encryption encryption = secure.get();
        return encryption != null && encryption.supportsEncryption() && !encryption.isStartTLS();
    }
}
