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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * {@link ChannelUpstreamHandler} which limit connections per IP
 * 
 * This handler must be used as singleton when adding it to the {@link ChannelPipeline} to work correctly
 *
 * TODO: Remove when its committed to NETTY. 
 *       https://jira.jboss.org/jira/browse/NETTY-311
 */
public class ConnectionPerIpLimitUpstreamHandler extends SimpleChannelUpstreamHandler{

    private final ConcurrentMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    private volatile int maxConnectionsPerIp = -1;
    
    public ConnectionPerIpLimitUpstreamHandler(int maxConnectionsPerIp) {
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }
    
    public int getConnections(String ip) {
        AtomicInteger count = connections.get(ip);
        if (count == null) {
            return 0;
        } else {
            return count.get();
        }
    }
    
    public void setMaxConnectionsPerIp(int maxConnectionsPerIp) {
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }
    
    
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {

        if (maxConnectionsPerIp > 0) {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            String remoteIp = remoteAddress.getAddress().getHostAddress();
            
            AtomicInteger atomicCount = connections.get(remoteIp);

            if (atomicCount == null) {
            	atomicCount = new AtomicInteger(1);
                AtomicInteger oldAtomicCount = connections.putIfAbsent(remoteIp, atomicCount);
                // if another thread put a new counter for this ip, we must use the other one.
                if (oldAtomicCount != null) {
                	atomicCount = oldAtomicCount;
                }
            } else {
                Integer count = atomicCount.incrementAndGet();
                if (count > maxConnectionsPerIp) {
                    ctx.getChannel().close();
                }
            }
        }
        
        super.channelOpen(ctx, e);
    }
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (maxConnectionsPerIp > 0) {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            String remoteIp = remoteAddress.getAddress().getHostAddress();
            
            AtomicInteger atomicCount = connections.get(remoteIp);
            if (atomicCount != null) {
                atomicCount.decrementAndGet();
            }              
            
        }
        super.channelClosed(ctx, e);
    }
}
