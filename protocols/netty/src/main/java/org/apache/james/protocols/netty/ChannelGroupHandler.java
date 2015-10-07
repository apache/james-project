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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;

/**
 * Add channels to the channel group after the channel was opened.
 * 
 * This handler is thread-safe and thus can be shared across pipelines
 *
 */
public final class ChannelGroupHandler extends SimpleChannelUpstreamHandler {
    private ChannelGroup channels;
    public ChannelGroupHandler(ChannelGroup channels) {
        this.channels = channels;
    }
   
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception{
        // Add all open channels to the global group so that they are
        // closed on shutdown.
        channels.add(e.getChannel());
        
        // call the next handler in the chain
        super.channelOpen(ctx, e);
    }

}