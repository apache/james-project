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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SimpleChannelUpstreamHandler} which handles IMAP
 */
public class ImapChannelUpstreamHandler extends SimpleChannelUpstreamHandler implements NettyConstants {
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

    private final ImapHeartbeatHandler heartbeatHandler = new ImapHeartbeatHandler();

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
    public void channelBound(final ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ImapSession imapsession = new NettyImapSession(ctx.getChannel(), secure, compress, authenticationConfiguration.isSSLRequired(),
            authenticationConfiguration.isPlainAuthEnabled(), SessionId.generate(),
            authenticationConfiguration.getOidcSASLConfiguration());
        MDCBuilder boundMDC = IMAPMDCContext.boundMDC(ctx);
        imapsession.setAttribute(MDC_KEY, boundMDC);
        attributes.set(ctx.getChannel(), imapsession);
        try (Closeable closeable = boundMDC.build()) {
            super.channelBound(ctx, e);
        }
    }

    private MDCBuilder mdc(ChannelHandlerContext ctx) {
        ImapSession maybeSession = (ImapSession) attributes.get(ctx.getChannel());

        return Optional.ofNullable(maybeSession)
            .map(session -> {
                MDCBuilder boundMDC = (MDCBuilder) session.getAttribute(MDC_KEY);

                return IMAPMDCContext.from(session)
                    .addToContext(boundMDC);
            })
            .orElseGet(MDCBuilder::create);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            LOGGER.info("Connection closed for {}", address.getAddress().getHostAddress());

            // remove the stored attribute for the channel to free up resources
            // See JAMES-1195
            ImapSession imapSession = (ImapSession) attributes.remove(ctx.getChannel());
            if (imapSession != null) {
                imapSession.logout();
            }
            imapConnectionsMetric.decrement();

            super.channelClosed(ctx, e);
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
            LOGGER.info("Connection established from {}", address.getAddress().getHostAddress());
            imapConnectionsMetric.increment();

            ImapResponseComposer response = new ImapResponseComposerImpl(new ChannelImapResponseWriter(ctx.getChannel()));
            ctx.setAttachment(response);

            // write hello to client
            response.untagged().message("OK").message(hello).end();
            super.channelConnected(ctx, e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            LOGGER.warn("Error while processing imap request", e.getCause());

            if (e.getCause() instanceof TooLongFrameException) {

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
                ImapResponseComposer composer = (ImapResponseComposer) ctx.getAttachment();
                composer.untaggedResponse(ImapConstants.BAD + " failed. Maximum command line length exceeded");

            } else {

                // logout on error not sure if that is the best way to handle it
                final ImapSession imapSession = (ImapSession) attributes.get(ctx.getChannel());
                if (imapSession != null) {
                    imapSession.logout();
                }

                // Make sure we close the channel after all the buffers were flushed out
                Channel channel = ctx.getChannel();
                if (channel.isConnected()) {
                    channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }

            }
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            imapCommandsMetric.increment();
            ImapSession session = (ImapSession) attributes.get(ctx.getChannel());
            ImapResponseComposer response = (ImapResponseComposer) ctx.getAttachment();
            ImapMessage message = (ImapMessage) event.getMessage();
            ChannelPipeline cp = ctx.getPipeline();

            try {
                try {
                    if (cp.get(NettyConstants.EXECUTION_HANDLER) != null) {
                        cp.addBefore(NettyConstants.EXECUTION_HANDLER, NettyConstants.HEARTBEAT_HANDLER, heartbeatHandler);
                    } else {
                        cp.addBefore(NettyConstants.CORE_HANDLER, NettyConstants.HEARTBEAT_HANDLER, heartbeatHandler);
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.info("heartbeat handler is already part of this pipeline", e);
                }
                final ResponseEncoder responseEncoder = new ResponseEncoder(encoder, response);
                processor.process(message, responseEncoder, session);

                if (session.getState() == ImapSessionState.LOGOUT) {
                    // Make sure we close the channel after all the buffers were flushed out
                    Channel channel = ctx.getChannel();
                    if (channel.isConnected()) {
                        channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
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
                    ctx.getPipeline().remove(NettyConstants.HEARTBEAT_HANDLER);
                } catch (NoSuchElementException e) {
                    LOGGER.info("Heartbeat handler was concurrently removed");
                }
                if (message instanceof Closeable) {
                    ((Closeable) message).close();
                }
            }

            super.messageReceived(ctx, event);
        }
    }

}
