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

import java.io.Closeable;
import java.net.InetSocketAddress;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.managesieve.transcode.NotEnoughDataException;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.protocols.api.Encryption;
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
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;

public class ManageSieveChannelUpstreamHandler extends SimpleChannelUpstreamHandler {

    static final String SSL_HANDLER = "sslHandler";

    private final Logger logger;
    private final ChannelLocal<Session> attributes;
    private final ManageSieveProcessor manageSieveProcessor;
    private final Encryption secure;

    public ManageSieveChannelUpstreamHandler(
            ManageSieveProcessor manageSieveProcessor, Encryption secure, Logger logger) {
        this.logger = logger;
        this.attributes = new ChannelLocal<>();
        this.manageSieveProcessor = manageSieveProcessor;
        this.secure = secure;
    }

    private boolean isSSL() {
        return secure != null && !secure.isStartTLS();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelManageSieveResponseWriter attachment = (ChannelManageSieveResponseWriter) ctx.getAttachment();
        try (Closeable closeable = ManageSieveMDCContext.from(ctx, attributes)) {
            String request = attachment.cumulate((String) e.getMessage());
            if (request.isEmpty() || request.startsWith("\r\n")) {
                return;
            }

            Session manageSieveSession = attributes.get(ctx.getChannel());
            String responseString = manageSieveProcessor.handleRequest(manageSieveSession, request);
            attachment.resetCumulation();
            attachment.write(responseString);
            if (manageSieveSession.getState() == Session.State.SSL_NEGOCIATION) {
                turnSSLon(ctx.getChannel());
                manageSieveSession.setSslEnabled(true);
                manageSieveSession.setState(Session.State.UNAUTHENTICATED);
                attachment.stopDetectingCommandInjection();
            }
        } catch (NotEnoughDataException ex) {
            // Do nothing will keep the cumulation
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx, attributes)) {
            logger.warn("Error while processing ManageSieve request", e.getCause());

            if (e.getCause() instanceof TooLongFrameException) {
                // Max line length exceeded
                // See also JAMES-1190
                ((ChannelManageSieveResponseWriter) ctx.getAttachment()).write("NO Maximum command line length exceeded");
            } else if (e.getCause() instanceof SessionTerminatedException) {
                ((ChannelManageSieveResponseWriter) ctx.getAttachment()).write("OK channel is closing");
                logout(ctx);
            }
        }
    }

    private void logout(ChannelHandlerContext ctx) {
        // logout on error not sure if that is the best way to handle it
        attributes.remove(ctx.getChannel());
        // Make sure we close the channel after all the buffers were flushed out
        Channel channel = ctx.getChannel();
        if (channel.isConnected()) {
            channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx, attributes)) {
            InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            logger.info("Connection established from {}", address.getAddress().getHostAddress());

            Session session = new SettableSession();
            if (isSSL()) {
                session.setSslEnabled(true);
            }
            attributes.set(ctx.getChannel(), session);
            ctx.setAttachment(new ChannelManageSieveResponseWriter(ctx.getChannel()));
            super.channelBound(ctx, e);
            ((ChannelManageSieveResponseWriter) ctx.getAttachment()).write(manageSieveProcessor.getAdvertisedCapabilities() + "OK\r\n");
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx, attributes)) {
            InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            logger.info("Connection closed for {}", address.getAddress().getHostAddress());
            attributes.remove(ctx.getChannel());
            super.channelClosed(ctx, e);
        }
    }

    private void turnSSLon(Channel channel) {
        if (secure != null) {
            channel.setReadable(false);
            SslHandler filter = new SslHandler(secure.createSSLEngine(), false);
            filter.getEngine().setUseClientMode(false);
            channel.getPipeline().addFirst(SSL_HANDLER, filter);
            channel.setReadable(true);
        }
    }
}
