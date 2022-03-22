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
package org.apache.james.protocols.lib.netty;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.netty.AbstractSSLAwareChannelPipelineFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.EventLoopGroupManager;

import io.netty.channel.ChannelHandler;

/**
 * Abstract base class which should get used if you MAY need an {@link ExecutionHandler}
 * 
 *
 */
@ChannelHandler.Sharable
public abstract class AbstractExecutorAwareChannelPipelineFactory extends AbstractSSLAwareChannelPipelineFactory {

    public AbstractExecutorAwareChannelPipelineFactory(int readTimeout, int maxConnections, int maxConnectsPerIp,
                                                       Encryption encryption,
                                                       ChannelHandlerFactory frameHandlerFactory, EventLoopGroupManager groupManager) {
        super(readTimeout, maxConnections, maxConnectsPerIp, encryption, frameHandlerFactory, groupManager);
    }
    
    /**
     * Return the {@link ConnectionCountHandler} to use
     * 
     * @return cHandler
     */
    protected abstract ConnectionCountHandler getConnectionCountHandler();

}
