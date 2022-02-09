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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Count connections
 */
@ChannelHandler.Sharable
public class ConnectionCountHandler extends ChannelInboundHandlerAdapter {

    public final AtomicInteger currentConnectionCount = new AtomicInteger();
    public final AtomicLong connectionsTillStartup = new AtomicLong();

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        currentConnectionCount.decrementAndGet();
        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        currentConnectionCount.incrementAndGet();
        connectionsTillStartup.incrementAndGet();
        super.channelActive(ctx);
    }

    /**
     * Return the count of the current open connections
     * 
     * @return count
     */
    public int getCurrentConnectionCount() {
        return currentConnectionCount.get();
    }

    /**
     * Return the count of all connections which where made till the server
     * was started
     * 
     * @return tillCount
     */
    public long getConnectionsTillStartup() {
        return connectionsTillStartup.get();
    }
}
