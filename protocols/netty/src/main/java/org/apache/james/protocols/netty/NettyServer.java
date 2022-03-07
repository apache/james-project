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

import javax.inject.Inject;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;

import com.google.common.base.Preconditions;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.group.ChannelGroup;


/**
 * Generic NettyServer 
 */
public class NettyServer extends AbstractAsyncServer {
    public static class Factory {
        private Protocol protocol;
        private Optional<Encryption> secure;
        private Optional<ChannelHandlerFactory> frameHandlerFactory;

        @Inject
        public Factory() {
            secure = Optional.empty();
            frameHandlerFactory = Optional.empty();
        }

        public Factory protocol(Protocol protocol) {
            Preconditions.checkNotNull(protocol, "'protocol' is mandatory");
            this.protocol = protocol;
            return this;
        }

        public Factory secure(Encryption secure) {
            this.secure = Optional.ofNullable(secure);
            return this;
        }

        public Factory frameHandlerFactory(ChannelHandlerFactory frameHandlerFactory) {
            this.frameHandlerFactory = Optional.ofNullable(frameHandlerFactory);
            return this;
        }

        public NettyServer build() {
            Preconditions.checkState(protocol != null, "'protocol' is mandatory");
            return new NettyServer(protocol, 
                    secure.orElse(null),
                    frameHandlerFactory.orElse(new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH)));
        }
    }

    protected final Encryption secure;
    protected final Protocol protocol;
    private final ChannelHandlerFactory frameHandlerFactory;
    private int maxCurConnections;
    private int maxCurConnectionsPerIP;
   
    private NettyServer(Protocol protocol, Encryption secure, ChannelHandlerFactory frameHandlerFactory) {
        this.protocol = protocol;
        this.secure = secure;
        this.frameHandlerFactory = frameHandlerFactory;
    }
    
    public void setMaxConcurrentConnections(int maxCurConnections) {
        if (isBound()) {
            throw new IllegalStateException("Server running already");
        }
        this.maxCurConnections = maxCurConnections;
    }

    public void setMaxConcurrentConnectionsPerIP(int maxCurConnectionsPerIP) {
        if (isBound()) {
            throw new IllegalStateException("Server running already");
        }
        this.maxCurConnectionsPerIP = maxCurConnectionsPerIP;
    }

    protected ChannelInboundHandlerAdapter createCoreHandler() {
        return new BasicChannelUpstreamHandler(new ProtocolMDCContextFactory.Standard(), protocol, secure);
    }
    
    @Override
    public synchronized void bind() throws Exception {
        super.bind();
    }

    private ChannelHandlerFactory getFrameHandlerFactory() {
        return frameHandlerFactory;
    }

    @Override
    protected AbstractChannelPipelineFactory createPipelineFactory(ChannelGroup group) {

        return new AbstractSSLAwareChannelPipelineFactory(
            getTimeout(),
            maxCurConnections,
            maxCurConnectionsPerIP,
            group,
            secure,
            getFrameHandlerFactory(),
            new DefaultEventLoopGroup(16)) {

            @Override
            protected ChannelInboundHandlerAdapter createHandler() {
                return createCoreHandler();
            }
        };

    }

}
