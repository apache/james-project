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

package org.apache.james.managesieveserver.netty;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.protocols.api.logger.ProtocolLoggerAdapter;
import org.apache.james.protocols.api.logger.ProtocolSessionLogger;
import org.apache.james.protocols.lib.Slf4jLoggerAdapter;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

public class ManageSieveChannelUpstreamHandler extends SimpleChannelUpstreamHandler {

    private final Logger logger;
    private final ChannelLocal<Session> attributes;
    private final ManageSieveProcessor manageSieveProcessor;

    public ManageSieveChannelUpstreamHandler(ManageSieveProcessor manageSieveProcessor, Logger logger) {
        this.logger = logger;
        this.attributes = new ChannelLocal<Session>();
        this.manageSieveProcessor = manageSieveProcessor;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        String request = (String) e.getMessage();
        Session manageSieveSession = attributes.get(ctx.getChannel());
        String responseString = manageSieveProcessor.handleRequest(manageSieveSession, request);
        ((ChannelManageSieveResponseWriter)ctx.getAttachment()).write(responseString);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        getLogger(ctx.getChannel()).warn("Error while processing imap request: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
        getLogger(ctx.getChannel()).debug("Error while processing imap request", e.getCause());

        if (e.getCause() instanceof TooLongFrameException) {
            // Max line length exceeded
            //
            // See also JAMES-1190
            ((ChannelManageSieveResponseWriter)ctx.getAttachment()).write("NO Maximum command line length exceeded");
        } else {
            // logout on error not sure if that is the best way to handle it
            attributes.remove(ctx.getChannel());

            // Make sure we close the channel after all the buffers were flushed out
            Channel channel = ctx.getChannel();
            if (channel.isConnected()) {
                channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        attributes.set(ctx.getChannel(), new SettableSession());
        ctx.setAttachment(new ChannelManageSieveResponseWriter(ctx.getChannel()));
        super.channelBound(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
        getLogger(ctx.getChannel()).info("Connection closed for " + address.getAddress().getHostAddress());
        attributes.remove(ctx.getChannel());
        super.channelClosed(ctx, e);
    }

    private Logger getLogger(Channel channel) {
        return new Slf4jLoggerAdapter(new ProtocolSessionLogger("" + channel.getId(), new ProtocolLoggerAdapter(logger)));
    }
}
