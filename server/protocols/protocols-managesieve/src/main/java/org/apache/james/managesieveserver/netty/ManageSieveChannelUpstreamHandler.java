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
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.netty.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;

@ChannelHandler.Sharable
public class ManageSieveChannelUpstreamHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveChannelUpstreamHandler.class);

    static final String SSL_HANDLER = "sslHandler";

    private final ManageSieveProcessor manageSieveProcessor;
    private final Encryption secure;
    private final int maxLineLength;

    public ManageSieveChannelUpstreamHandler(
        ManageSieveProcessor manageSieveProcessor, Encryption secure, int maxLineLength) {
        this.manageSieveProcessor = manageSieveProcessor;
        this.secure = secure;
        this.maxLineLength = maxLineLength;
    }

    private boolean isSSL() {
        return secure != null && !secure.isStartTLS();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ChannelManageSieveResponseWriter attachment = ctx.channel().attr(NettyConstants.RESPONSE_WRITER_ATTRIBUTE_KEY).get();
        try (Closeable closeable = ManageSieveMDCContext.from(ctx)) {
            if (msg instanceof HAProxyMessage) {
                handleHAProxyMessage(ctx, (HAProxyMessage) msg);
                return;
            }
            String request = attachment.cumulate((String) msg);
            if (request.isEmpty() || request.startsWith("\r\n")) {
                return;
            }
            if (request.length() > maxLineLength) {
                throw new TooLongFrameException();
            }

            Session manageSieveSession = ctx.channel().attr(NettyConstants.SESSION_ATTRIBUTE_KEY).get();
            String responseString = manageSieveProcessor.handleRequest(manageSieveSession, request);
            attachment.resetCumulation();
            attachment.write(responseString);
            if (manageSieveSession.getState() == Session.State.SSL_NEGOCIATION) {
                turnSSLon(ctx.channel());
                manageSieveSession.setSslEnabled(true);
                manageSieveSession.setState(Session.State.UNAUTHENTICATED);
                attachment.stopDetectingCommandInjection();
            }
        } catch (NotEnoughDataException ex) {
            // Do nothing will keep the cumulation
        }
    }

    private void handleHAProxyMessage(ChannelHandlerContext ctx, HAProxyMessage haproxyMsg) throws Exception {
        try {
            if (haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP4) || haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP6)) {
                ProxyInformation proxyInformation = new ProxyInformation(
                    new InetSocketAddress(haproxyMsg.sourceAddress(), haproxyMsg.sourcePort()),
                    new InetSocketAddress(haproxyMsg.destinationAddress(), haproxyMsg.destinationPort()));
                ctx.channel().attr(NettyConstants.PROXY_INFO).set(proxyInformation);

                LOGGER.info("Connection from {} runs through {} proxy", haproxyMsg.sourceAddress(), haproxyMsg.destinationAddress());
            } else {
                throw new IllegalArgumentException("Only TCP4/TCP6 are supported when using PROXY protocol.");
            }

            super.channelReadComplete(ctx);
        } finally {
            haproxyMsg.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx)) {
            LOGGER.warn("Error while processing ManageSieve request", cause);

            if (cause instanceof TooLongFrameException) {
                // Max line length exceeded
                // See also JAMES-1190
                ctx.channel().attr(NettyConstants.RESPONSE_WRITER_ATTRIBUTE_KEY).get().write("NO Maximum command line length exceeded");
            } else if (cause instanceof SessionTerminatedException) {
                ctx.channel().attr(NettyConstants.RESPONSE_WRITER_ATTRIBUTE_KEY).get().write("OK channel is closing");
                logout(ctx);
            }
        }
    }

    private void logout(ChannelHandlerContext ctx) {
        // logout on error not sure if that is the best way to handle it
        ctx.channel().attr(NettyConstants.SESSION_ATTRIBUTE_KEY).getAndSet(null);
        // Make sure we close the channel after all the buffers were flushed out
        Channel channel = ctx.channel();
        if (channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx)) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection established from {}", address.getAddress().getHostAddress());

            Session session = new SettableSession();
            if (isSSL()) {
                session.setSslEnabled(true);
            }
            ctx.channel().attr(NettyConstants.SESSION_ATTRIBUTE_KEY).set(session);
            ctx.channel().attr(NettyConstants.RESPONSE_WRITER_ATTRIBUTE_KEY).set(new ChannelManageSieveResponseWriter(ctx.channel()));
            super.channelActive(ctx);
            ctx.channel().attr(NettyConstants.RESPONSE_WRITER_ATTRIBUTE_KEY).get().write(manageSieveProcessor.getAdvertisedCapabilities() + "OK\r\n");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try (Closeable closeable = ManageSieveMDCContext.from(ctx)) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection closed for {}", address.getAddress().getHostAddress());
            ctx.channel().attr(NettyConstants.SESSION_ATTRIBUTE_KEY).getAndSet(null);
            super.channelInactive(ctx);
        }
    }

    private void turnSSLon(Channel channel) {
        if (secure != null) {
            channel.config().setAutoRead(false);
            channel.pipeline().addFirst(SSL_HANDLER, secure.sslHandler());
            channel.config().setAutoRead(true);
        }
    }
}
