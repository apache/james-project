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

import static org.apache.james.imapserver.netty.IMAPServer.AuthenticationConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.ImapSession.SessionId;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.main.ResponseEncoder;
import org.apache.james.metrics.api.Metric;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.TooLongFrameException;

/**
 * {@link ChannelInboundHandlerAdapter} which handles IMAP
 */
@ChannelHandler.Sharable
public class ImapChannelUpstreamHandler extends ChannelInboundHandlerAdapter implements NettyConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapChannelUpstreamHandler.class);
    public static final String MDC_KEY = "bound_MDC";

    public static class ImapChannelUpstreamHandlerBuilder {
        private String hello;
        private Encryption secure;
        private boolean compress;
        private ImapProcessor processor;
        private ImapEncoder encoder;
        private IMAPServer.AuthenticationConfiguration authenticationConfiguration;
        private ImapMetrics imapMetrics;

        public ImapChannelUpstreamHandlerBuilder hello(String hello) {
            this.hello = hello;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder secure(Encryption secure) {
            this.secure = secure;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder compress(boolean compress) {
            this.compress = compress;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder processor(ImapProcessor processor) {
            this.processor = processor;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder encoder(ImapEncoder encoder) {
            this.encoder = encoder;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder authenticationConfiguration(IMAPServer.AuthenticationConfiguration authenticationConfiguration) {
            this.authenticationConfiguration = authenticationConfiguration;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder imapMetrics(ImapMetrics imapMetrics) {
            this.imapMetrics = imapMetrics;
            return this;
        }

        public ImapChannelUpstreamHandler build() {
            return new ImapChannelUpstreamHandler(hello, processor, encoder, compress, secure, imapMetrics, authenticationConfiguration);
        }
    }

    public static ImapChannelUpstreamHandlerBuilder builder() {
        return new ImapChannelUpstreamHandlerBuilder();
    }

    private final String hello;

    private final Encryption secure;

    private final boolean compress;

    private final ImapProcessor processor;

    private final ImapEncoder encoder;

    private final ImapHeartbeatHandler heartbeatHandler = new ImapHeartbeatHandler(0,0,0);

    private final AuthenticationConfiguration authenticationConfiguration;

    private final Metric imapConnectionsMetric;

    private final Metric imapCommandsMetric;

    public ImapChannelUpstreamHandler(String hello, ImapProcessor processor, ImapEncoder encoder, boolean compress,
                                      Encryption secure, ImapMetrics imapMetrics, AuthenticationConfiguration authenticationConfiguration) {
        this.hello = hello;
        this.processor = processor;
        this.encoder = encoder;
        this.secure = secure;
        this.compress = compress;
        this.authenticationConfiguration = authenticationConfiguration;
        this.imapConnectionsMetric = imapMetrics.getConnectionsMetric();
        this.imapCommandsMetric = imapMetrics.getCommandsMetric();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ImapSession imapsession = new NettyImapSession(ctx.channel(), secure, compress, authenticationConfiguration.isSSLRequired(),
            authenticationConfiguration.isPlainAuthEnabled(), SessionId.generate(),
            authenticationConfiguration.getOidcSASLConfiguration());
        MDCBuilder boundMDC = IMAPMDCContext.boundMDC(ctx);
        imapsession.setAttribute(MDC_KEY, boundMDC);
        ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).set(imapsession);
        try (Closeable closeable = boundMDC.build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection established from {}", address.getAddress().getHostAddress());
            imapConnectionsMetric.increment();

            ImapResponseComposer response = new ImapResponseComposerImpl(new ChannelImapResponseWriter(ctx.channel()));
            ctx.channel().attr(CONTEXT_ATTACHMENT_ATTRIBUTE_KEY).set(response);

            // write hello to client
            response.untagged().message("OK").message(hello).end();
            super.channelActive(ctx);
        }

    }

    private MDCBuilder mdc(ChannelHandlerContext ctx) {
        ImapSession maybeSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();

        return Optional.ofNullable(maybeSession)
            .map(session -> {
                MDCBuilder boundMDC = (MDCBuilder) session.getAttribute(MDC_KEY);

                return IMAPMDCContext.from(session)
                    .addToContext(boundMDC);
            })
            .orElseGet(MDCBuilder::create);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection closed for {}", address.getAddress().getHostAddress());

            // remove the stored attribute for the channel to free up resources
            // See JAMES-1195
            ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).getAndSet(null);
            if (imapSession != null) {
                imapSession.logout();
            }
            imapConnectionsMetric.decrement();

            super.channelInactive(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            LOGGER.warn("Error while processing imap request", cause);

            if (cause instanceof TooLongFrameException) {

                // Max line length exceeded
                // See RFC 2683 section 3.2.1
                //
                // "For its part, a server should allow for a command line of at
                // least
                // 8000 octets. This provides plenty of leeway for accepting
                // reasonable
                // length commands from clients. The server should send a BAD
                // response
                // to a command that does not end within the server's maximum
                // accepted
                // command length."
                //
                // See also JAMES-1190
                ImapResponseComposer composer = (ImapResponseComposer) ctx.channel().attr(CONTEXT_ATTACHMENT_ATTRIBUTE_KEY).get();
                composer.untaggedResponse(ImapConstants.BAD + " failed. Maximum command line length exceeded");

            } else {

                // logout on error not sure if that is the best way to handle it
                final ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
                if (imapSession != null) {
                    imapSession.logout();
                }

                // Make sure we close the channel after all the buffers were flushed out
                Channel channel = ctx.channel();
                if (channel.isActive()) {
                    channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }

            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            imapCommandsMetric.increment();
            ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
            ImapResponseComposer response = (ImapResponseComposer) ctx.channel().attr(CONTEXT_ATTACHMENT_ATTRIBUTE_KEY).get();
            ImapMessage message = (ImapMessage) msg;
            ChannelPipeline cp = ctx.pipeline();

            try {
                try {
                    cp.addBefore(NettyConstants.CORE_HANDLER, NettyConstants.HEARTBEAT_HANDLER, heartbeatHandler);
                } catch (IllegalArgumentException e) {
                    LOGGER.info("heartbeat handler is already part of this pipeline", e);
                }
                final ResponseEncoder responseEncoder = new ResponseEncoder(encoder, response);
                processor.process(message, responseEncoder, session);

                if (session.getState() == ImapSessionState.LOGOUT) {
                    // Make sure we close the channel after all the buffers were flushed out
                    Channel channel = ctx.channel();
                    if (channel.isActive()) {
                        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    }
                }
                final IOException failure = responseEncoder.getFailure();

                if (failure != null) {
                    LOGGER.info(failure.getMessage());
                    LOGGER.debug("Failed to write {}", message, failure);
                    throw failure;
                }
            } finally {
                try {
                    ctx.pipeline().remove(NettyConstants.HEARTBEAT_HANDLER);
                } catch (NoSuchElementException e) {
                    LOGGER.info("Heartbeat handler was concurrently removed");
                }
                if (message instanceof Closeable) {
                    ((Closeable) message).close();
                }
            }

            super.channelReadComplete(ctx);
        }
    }

}
