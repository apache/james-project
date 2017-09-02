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
package org.apache.james.imapserver.netty;

import java.net.InetSocketAddress;

import org.apache.james.imap.api.process.ImapSession;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.timeout.IdleState;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IdleStateAwareChannelHandler} which will call {@link ImapSession#logout()} if the
 * connected client did not receive or send any traffic in a given timeframe.
 */
public class ImapIdleStateHandler extends IdleStateAwareChannelHandler implements NettyConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapIdleStateHandler.class);

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) throws Exception {

        // check if the client did nothing for too long
        if (e.getState().equals(IdleState.ALL_IDLE)) {
            ImapSession session = (ImapSession) attributes.get(ctx.getChannel());
            InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();

            LOGGER.info("Logout client {} ({}) because it idled for too long...",
                address.getHostName(),
                address.getAddress().getHostAddress());

            // logout the client
            session.logout();

            // close the channel
            ctx.getChannel().close();

        }
        
        super.channelIdle(ctx, e);
    
    }

}
