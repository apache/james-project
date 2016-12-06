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


import javax.net.ssl.SSLContext;

import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;


/**
 * Generic NettyServer 
 */
public class NettyServer extends AbstractAsyncServer {

    protected final Protocol protocol;
    
    private ExecutionHandler eHandler;
    
    private ChannelUpstreamHandler coreHandler;

    protected final Encryption secure;

    private int maxCurConnections;

    private int maxCurConnectionsPerIP;
   
    public NettyServer(Protocol protocol) {
        this(protocol, null);
    }
    
    
    public NettyServer(Protocol protocol, Encryption secure) {
        super();
        this.protocol = protocol;
        this.secure = secure;
    }
    
    protected ExecutionHandler createExecutionHandler(int size) {
        return new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(size, 0, 0));
    }
    
    
    /**
     * Set true if an ExecutionHandler should be used to hand over the tasks. This should be done if you have some {@link ProtocolHandler}'s which need to full fill some blocking operation.
     * 
     * @param useHandler <code>true</code> if an ExecutionHandler should be used
     * @param size the thread count to use
     */
    public void setUseExecutionHandler(boolean useHandler, int size) {
        if (isBound()) throw new IllegalStateException("Server running already");
        if (useHandler) {
            eHandler = createExecutionHandler(size);
        } else {
            if (eHandler != null) {
                eHandler.releaseExternalResources();
            }
            eHandler = null;
        }
    }
    
    public void setMaxConcurrentConnections(int maxCurConnections) {
        if (isBound()) throw new IllegalStateException("Server running already");
        this.maxCurConnections = maxCurConnections;
    }
  
    public void setMaxConcurrentConnectionsPerIP(int maxCurConnectionsPerIP) {
        if (isBound()) throw new IllegalStateException("Server running already");
        this.maxCurConnectionsPerIP = maxCurConnectionsPerIP;
    }
    
    protected ChannelUpstreamHandler createCoreHandler() {
        return new BasicChannelUpstreamHandler(protocol, secure);
    }
    
    @Override
    public synchronized void bind() throws Exception {
        coreHandler = createCoreHandler();
        super.bind();
    }


    @Override
    protected ChannelPipelineFactory createPipelineFactory(ChannelGroup group) {

        return new AbstractSSLAwareChannelPipelineFactory(getTimeout(), maxCurConnections, maxCurConnectionsPerIP, group, eHandler) {

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
