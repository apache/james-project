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
import javax.net.ssl.SSLContext;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.util.HashedWheelTimer;

import com.google.common.base.Preconditions;


/**
 * Generic NettyServer 
 */
public class NettyServer extends AbstractAsyncServer {

    public static class Factory {

        private final HashedWheelTimer hashedWheelTimer;

        private Protocol protocol;
        private Optional<Encryption> secure;
        private Optional<ChannelHandlerFactory> frameHandlerFactory;

        @Inject
        public Factory(HashedWheelTimer hashedWheelTimer) {
            this.hashedWheelTimer = hashedWheelTimer;
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
                    frameHandlerFactory.orElse(new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH)),
                    hashedWheelTimer);
        }
    }

    protected final Encryption secure;
    protected final Protocol protocol;
    private final ChannelHandlerFactory frameHandlerFactory;
    private final HashedWheelTimer hashedWheelTimer;

    private ExecutionHandler eHandler;
    
    private ChannelUpstreamHandler coreHandler;

    private int maxCurConnections;

    private int maxCurConnectionsPerIP;
   
    private NettyServer(Protocol protocol, Encryption secure, ChannelHandlerFactory frameHandlerFactory, HashedWheelTimer hashedWheelTimer) {
        this.protocol = protocol;
        this.secure = secure;
        this.frameHandlerFactory = frameHandlerFactory;
        this.hashedWheelTimer = hashedWheelTimer;
    }
    
    protected ChannelUpstreamHandler createCoreHandler() {
        return new BasicChannelUpstreamHandler(protocol, secure);
    }
    
    @Override
    public synchronized void bind() throws Exception {
        coreHandler = createCoreHandler();
        super.bind();
    }

    private ChannelHandlerFactory getFrameHandlerFactory() {
        return frameHandlerFactory;
    }

    @Override
    protected ChannelPipelineFactory createPipelineFactory(ChannelGroup group) {

        return new AbstractSSLAwareChannelPipelineFactory(
            getTimeout(),
            maxCurConnections,
            maxCurConnectionsPerIP,
            group,
            secure != null ? secure.getEnabledCipherSuites() : null,
            eHandler,
            getFrameHandlerFactory(),
            hashedWheelTimer) {

            @Override
            protected ChannelUpstreamHandler createHandler() {
                return coreHandler;
            }

            @Override
            protected boolean isSSLSocket() {
                return getSSLContext() != null && secure != null && !secure.isStartTLS();
            }

            @Override
            protected SSLContext getSSLContext() {
                if (secure != null) {
                    return secure.getContext();
                } else  {
                    return null;
                }
            }
        };

    }

}
