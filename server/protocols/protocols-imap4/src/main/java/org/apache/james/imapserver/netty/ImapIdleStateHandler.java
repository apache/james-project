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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import reactor.core.publisher.Mono;

/**
 * {@link IdleStateHandler} which will call {@link ImapSession#logout()} if the
 * connected client did not receive or send any traffic in a given timeframe.
 */
@ChannelHandler.Sharable
public class ImapIdleStateHandler extends IdleStateHandler implements NettyConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapIdleStateHandler.class);

    public ImapIdleStateHandler(int allIdleTimeSeconds) {
        this(0, 0, allIdleTimeSeconds);
    }

    public ImapIdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds);
    }

    @Override
    public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) throws Exception {

        // check if the client did nothing for too long
        if (e.state().equals(IdleState.ALL_IDLE)) {
            ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();

            LOGGER.info("Logout client {} ({}) because it idled for too long...",
                address.getHostName(),
                address.getAddress().getHostAddress());
            session.cancelOngoingProcessing();
            // logout the client
            session.logout()
                // close the channel
                .then(Mono.fromRunnable(() -> ctx.channel().close()))
                .then(Mono.fromRunnable(Throwing.runnable(() -> super.channelIdle(ctx, e))))
                .subscribe(any -> {

                }, ctx::fireExceptionCaught);
        } else {
            super.channelIdle(ctx, e);
        }
    
    }

}
