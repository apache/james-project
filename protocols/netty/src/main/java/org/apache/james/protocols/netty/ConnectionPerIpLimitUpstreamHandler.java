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

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * {@link ChannelInboundHandlerAdapter} which limit connections per IP
 * 
 * This handler must be used as singleton when adding it to the {@link ChannelPipeline} to work correctly
 *
 */
@ChannelHandler.Sharable
public class ConnectionPerIpLimitUpstreamHandler extends ChannelInboundHandlerAdapter {
    private static final String CONNECTION_LIMIT_PER_IP_HANDLER = "connectionPerIpLimitHandler";

    public static void addToPipeline(ChannelPipeline pipeline, int connPerIP) {
        if (connPerIP > 0) {
            pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, new ConnectionPerIpLimitUpstreamHandler(connPerIP));
        }
    }

    private final ConcurrentMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    private final int maxConnectionsPerIp;
    
    private ConnectionPerIpLimitUpstreamHandler(int maxConnectionsPerIp) {
        Preconditions.checkArgument(maxConnectionsPerIp > 0);
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        String remoteIp = remoteAddress.getAddress().getHostAddress();

        AtomicInteger atomicCount = connections.get(remoteIp);

        if (atomicCount == null) {
            atomicCount = new AtomicInteger(1);
            AtomicInteger oldAtomicCount = connections.putIfAbsent(remoteIp, atomicCount);
            // if another thread put a new counter for this ip, we must use the other one.
            if (oldAtomicCount != null) {
                oldAtomicCount.incrementAndGet();
            }
        } else {
            Integer count = atomicCount.incrementAndGet();
            if (count > maxConnectionsPerIp) {
                ctx.channel().close();
            }
        }
        
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (maxConnectionsPerIp > 0) {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            String remoteIp = remoteAddress.getAddress().getHostAddress();
            
            AtomicInteger atomicCount = connections.get(remoteIp);
            if (atomicCount != null) {
                atomicCount.decrementAndGet();
            }              
            
        }
        super.channelInactive(ctx);
    }
}
