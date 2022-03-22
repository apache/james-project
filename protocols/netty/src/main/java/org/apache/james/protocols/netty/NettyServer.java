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

import javax.inject.Inject;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;

import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Generic NettyServer 
 */
public final class NettyServer extends AbstractAsyncServer {

    private final Protocol protocol;
    private final Encryption secure;
    private final ChannelHandlerFactory frameHandlerFactory;
    private final int readTimeout;
    private final int maxConcurrentConnections;
    private final int maxConcurrentConnectionsPerIP;

    protected NettyServer(Factory factory) {
        super(factory);
        protocol = factory.protocol;
        secure = factory.secure;
        frameHandlerFactory = factory.frameHandlerFactory;
        readTimeout = factory.readTimeout;
        maxConcurrentConnections = factory.maxConcurrentConnections;
        maxConcurrentConnectionsPerIP = factory.maxConcurrentConnectionsPerIP;
    }

    @Override
    protected AbstractChannelPipelineFactory createChannelInitializer() {
        return new AbstractSSLAwareChannelPipelineFactory(
                readTimeout,
                maxConcurrentConnections,
                maxConcurrentConnectionsPerIP,
                secure,
                frameHandlerFactory,
                groupsManager) {

            @Override
            protected ChannelInboundHandlerAdapter createHandler() {
                return new BasicChannelInboundHandler(new ProtocolMDCContextFactory.Standard(), protocol, secure);
            }
        };
    }

    public static final class Factory extends AbstractAsyncServer.Factory<Factory> {
        protected Protocol protocol;
        protected Encryption secure;
        protected ChannelHandlerFactory frameHandlerFactory;
        protected int readTimeout;
        protected int maxConcurrentConnections;
        protected int maxConcurrentConnectionsPerIP;

        @Inject
        public Factory() {
            // Do nothing
        }

        @Override
        protected void beforeBuild() {
            super.beforeBuild();
            if (protocol == null) {
                throw new IllegalStateException("Must provide protocol");
            }
            if (frameHandlerFactory == null) {
                frameHandlerFactory = new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
            }
        }

        public Factory protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        public Factory secure(Encryption secure) {
            this.secure = secure;
            return this;
        }

        public Factory frameHandlerFactory(ChannelHandlerFactory frameHandlerFactory) {
            this.frameHandlerFactory = frameHandlerFactory;
            return this;
        }

        public Factory readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Factory maxConcurrentConnections(int maxConcurrentConnections) {
            this.maxConcurrentConnections = maxConcurrentConnections;
            return this;
        }

        public Factory maxConcurrentConnectionsPerIP(int maxConcurrentConnectionsPerIP) {
            this.maxConcurrentConnectionsPerIP = maxConcurrentConnectionsPerIP;
            return this;
        }

        public NettyServer build() {
            beforeBuild();
            return new NettyServer(this);
        }

        @Override
        protected final Factory this_() {
            return this;
        }
    }

    @Override
    public int getTimeout() {
        return readTimeout;
    }

}
