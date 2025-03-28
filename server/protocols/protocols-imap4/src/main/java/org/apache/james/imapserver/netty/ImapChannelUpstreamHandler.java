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

import static org.apache.james.imap.api.process.ImapSession.MDC_KEY;
import static org.apache.james.imapserver.netty.IMAPServer.AuthenticationConfiguration;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLHandshakeException;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.ImapSession.SessionId;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.main.ResponseEncoder;
import org.apache.james.imap.message.request.AbstractImapRequest;
import org.apache.james.imap.message.response.ImmutableStatusResponse;
import org.apache.james.metrics.api.Metric;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.Attribute;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link ChannelInboundHandlerAdapter} which handles IMAP
 */
@ChannelHandler.Sharable
public class ImapChannelUpstreamHandler extends ChannelInboundHandlerAdapter implements NettyConstants {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapChannelUpstreamHandler.class);

    public static class ImapChannelUpstreamHandlerBuilder {
        private String hello;
        private Encryption secure;
        private boolean compress;
        private ImapProcessor processor;
        private ImapEncoder encoder;
        private IMAPServer.AuthenticationConfiguration authenticationConfiguration;
        private ImapMetrics imapMetrics;
        private boolean ignoreIDLEUponProcessing;
        private Duration heartbeatInterval;
        private ReactiveThrottler reactiveThrottler;
        private Set<ConnectionCheck> connectionChecks;
        private boolean proxyRequired;
        private ChannelGroup imapChannelGroup;

        public ImapChannelUpstreamHandlerBuilder reactiveThrottler(ReactiveThrottler reactiveThrottler) {
            this.reactiveThrottler = reactiveThrottler;
            return this;
        }

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

        public ImapChannelUpstreamHandlerBuilder connectionChecks(Set<ConnectionCheck> connectionChecks) {
            this.connectionChecks = connectionChecks;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder imapMetrics(ImapMetrics imapMetrics) {
            this.imapMetrics = imapMetrics;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder ignoreIDLEUponProcessing(boolean ignoreIDLEUponProcessing) {
            this.ignoreIDLEUponProcessing = ignoreIDLEUponProcessing;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder proxyRequired(boolean proxyRequired) {
            this.proxyRequired = proxyRequired;
            return this;
        }

        public ImapChannelUpstreamHandlerBuilder imapChannelGroup(ChannelGroup imapChannelGroup) {
            this.imapChannelGroup = imapChannelGroup;
            return this;
        }

        public ImapChannelUpstreamHandler build() {
            return new ImapChannelUpstreamHandler(hello, processor, encoder, compress, secure, imapMetrics, authenticationConfiguration, ignoreIDLEUponProcessing, (int) heartbeatInterval.toSeconds(), reactiveThrottler, connectionChecks, proxyRequired, imapChannelGroup);
        }
    }

    static class ImapLinerarizer {
        private final AtomicBoolean isExecutingRequest = new AtomicBoolean(false);
        private final ConcurrentLinkedQueue<Object> throttled = new ConcurrentLinkedQueue<>();
    }

    public static ImapChannelUpstreamHandlerBuilder builder() {
        return new ImapChannelUpstreamHandlerBuilder();
    }

    private final String hello;
    private final Encryption secure;
    private final boolean compress;
    private final ImapProcessor processor;
    private final ImapEncoder encoder;
    private final ImapHeartbeatHandler heartbeatHandler;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final Metric imapConnectionsMetric;
    private final Metric imapCommandsMetric;
    private final boolean ignoreIDLEUponProcessing;
    private final ReactiveThrottler reactiveThrottler;
    private final Set<ConnectionCheck> connectionChecks;
    private final boolean proxyRequired;
    private final ChannelGroup imapChannelGroup;

    public ImapChannelUpstreamHandler(String hello, ImapProcessor processor, ImapEncoder encoder, boolean compress,
                                      Encryption secure, ImapMetrics imapMetrics, AuthenticationConfiguration authenticationConfiguration,
                                      boolean ignoreIDLEUponProcessing, int heartbeatIntervalSeconds, ReactiveThrottler reactiveThrottler,
                                      Set<ConnectionCheck> connectionChecks, boolean proxyRequired, ChannelGroup imapChannelGroup) {
        this.hello = hello;
        this.processor = processor;
        this.encoder = encoder;
        this.secure = secure;
        this.compress = compress;
        this.authenticationConfiguration = authenticationConfiguration;
        this.imapConnectionsMetric = imapMetrics.getConnectionsMetric();
        this.imapCommandsMetric = imapMetrics.getCommandsMetric();
        this.ignoreIDLEUponProcessing = ignoreIDLEUponProcessing;
        this.heartbeatHandler = new ImapHeartbeatHandler(heartbeatIntervalSeconds, heartbeatIntervalSeconds, heartbeatIntervalSeconds);
        this.reactiveThrottler = reactiveThrottler;
        this.connectionChecks = connectionChecks;
        this.proxyRequired = proxyRequired;
        this.imapChannelGroup = imapChannelGroup;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        imapChannelGroup.add(ctx.channel());
        SessionId sessionId = SessionId.generate();
        ImapSession imapsession = new NettyImapSession(ctx.channel(), secure, compress, authenticationConfiguration.isSSLRequired(),
            authenticationConfiguration.isPlainAuthEnabled(), sessionId,
            authenticationConfiguration.getOidcSASLConfiguration());
        ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).set(imapsession);
        ctx.channel().attr(REQUEST_COUNTER).set(new AtomicLong());
        ctx.channel().attr(LINEARIZER_ATTRIBUTE_KEY).set(new ImapLinerarizer());
        MDCBuilder boundMDC = IMAPMDCContext.boundMDC(ctx)
            .addToContext(MDCBuilder.SESSION_ID, sessionId.asString());
        imapsession.setAttribute(MDC_KEY, boundMDC);

        performConnectionCheck(imapsession.getRemoteAddress());

        try (Closeable closeable = mdc(imapsession).build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection established from {}", address.getAddress().getHostAddress());
            imapConnectionsMetric.increment();

            ChannelImapResponseWriter writer = new ChannelImapResponseWriter(ctx.channel(), imapsession);
            ImapResponseComposerImpl response = new ImapResponseComposerImpl(writer);
            // write hello to client
            response.untagged().message("OK").message(hello).end();
            response.flush();
            super.channelActive(ctx);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            Optional.ofNullable(ctx.channel().attr(BACKPRESSURE_CALLBACK).get())
                .ifPresent(Runnable::run);
        }
    }

    private void performConnectionCheck(InetSocketAddress clientIp) {
        if (!connectionChecks.isEmpty() && !proxyRequired) {
            Flux.fromIterable(connectionChecks)
                .concatMap(connectionCheck -> connectionCheck.validate(clientIp))
                .then()
                .block();
        }
    }

    private MDCBuilder mdc(ChannelHandlerContext ctx) {
        ImapSession maybeSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();

        return mdc(maybeSession);
    }

    private MDCBuilder mdc(ImapSession imapSession) {
        return Optional.ofNullable(imapSession)
            .map(ImapSession::mdc)
            .orElseGet(MDCBuilder::create);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // remove the stored attribute for the channel to free up resources
        // See JAMES-1195
        imapChannelGroup.remove(ctx.channel());
        ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).getAndSet(null);
        try (Closeable closeable = mdc(imapSession).build()) {
            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            LOGGER.info("Connection closed for {} and user {}", address.getAddress().getHostAddress(), retrieveUsername(imapSession));

            Optional.ofNullable(imapSession).ifPresent(ImapSession::cancelOngoingProcessing);
            Optional.ofNullable(imapSession)
                .map(ImapSession::logout)
                .orElse(Mono.empty())
                .doFinally(Throwing.consumer(signal -> {
                    imapConnectionsMetric.decrement();
                    super.channelInactive(ctx);
                }))
                .subscribe(any -> {

                }, ctx::fireExceptionCaught);
        }
    }

    static String retrieveUsername(ImapSession imapSession) {
        return Optional.ofNullable(imapSession)
            .flatMap(session -> Optional.ofNullable(session.getUserName()))
            .map(Username::asString)
            .orElse("");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).getAndSet(null);
        String username = retrieveUsername(imapSession);
        try (Closeable closeable = mdc(imapSession).build()) {
            if (cause instanceof SocketException) {
                LOGGER.info("Socket exception encountered for user {}: {}", username, cause.getMessage());
            } else if (isSslHandshkeException(cause)) {
                LOGGER.info("SSH handshake rejected {}", cause.getMessage());
            } else if (isNotSslRecordException(cause)) {
                LOGGER.info("Not an SSL record {}", cause.getMessage());
            } else if (!(cause instanceof ClosedChannelException)) {
                LOGGER.error("Error while processing imap request", cause);
            }

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
                ChannelImapResponseWriter writer = new ChannelImapResponseWriter(ctx.channel(), imapSession);
                ImapResponseComposerImpl response = new ImapResponseComposerImpl(writer);
                response.untaggedResponse(ImapConstants.BAD + " failed. Maximum command line length exceeded");
                response.flush();

            } else if (cause instanceof ReactiveThrottler.RejectedException) {
                manageRejectedException(ctx, (ReactiveThrottler.RejectedException) cause);
            } else {
                manageUnknownError(ctx);
            }
        }
    }

    private boolean isSslHandshkeException(Throwable cause) {
        return cause instanceof DecoderException
            && cause.getCause() instanceof SSLHandshakeException;
    }

    private boolean isNotSslRecordException(Throwable cause) {
        return cause instanceof DecoderException &&
            cause.getCause() instanceof NotSslRecordException;
    }

    private void manageRejectedException(ChannelHandlerContext ctx, ReactiveThrottler.RejectedException cause) throws IOException {
        if (cause.getImapMessage() instanceof AbstractImapRequest) {
            ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
            AbstractImapRequest req = (AbstractImapRequest) cause.getImapMessage();
            ChannelImapResponseWriter writer = new ChannelImapResponseWriter(ctx.channel(), imapSession);
            ImapResponseComposerImpl response = new ImapResponseComposerImpl(writer);
            new ResponseEncoder(encoder, response)
                .respond(new ImmutableStatusResponse(StatusResponse.Type.NO, req.getTag(), req.getCommand(),
                    new HumanReadableText(cause.getClass().getName(), cause.getMessage()), null));
            response.flush();
        } else {
            manageUnknownError(ctx);
        }
    }

    private void manageUnknownError(ChannelHandlerContext ctx) {
        // logout on error not sure if that is the best way to handle it
        ImapSession imapSession = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
        Optional.ofNullable(imapSession).ifPresent(ImapSession::cancelOngoingProcessing);

        Optional.ofNullable(imapSession)
            .map(ImapSession::logout)
            .orElse(Mono.empty())
            .doFinally(Throwing.consumer(signal -> {
                // Make sure we close the channel after all the buffers were flushed out
                Channel channel = ctx.channel();
                if (channel.isActive()) {
                    channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
                super.channelInactive(ctx);
            }))
            .subscribe(any -> {

            }, e -> {
                LOGGER.error("Exception while handling errors for channel {} and user {}", ctx.channel(),
                    Optional.ofNullable(imapSession).map(u -> u.getUserName().asString()).orElse(""), e);
                Channel channel = ctx.channel();
                if (channel.isActive()) {
                    channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        imapCommandsMetric.increment();
        ImapSession session = ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY).get();
        Attribute<Disposable> disposableAttribute = ctx.channel().attr(REQUEST_IN_FLIGHT_ATTRIBUTE_KEY);
        Optional.ofNullable(ctx.channel().attr(REQUEST_COUNTER))
            .flatMap(counter -> Optional.ofNullable(counter.get()))
            .ifPresent(AtomicLong::incrementAndGet);

        ImapLinerarizer linearalizer = ctx.channel().attr(LINEARIZER_ATTRIBUTE_KEY).get();
        synchronized (linearalizer) {
            if (linearalizer.isExecutingRequest.get()) {
                linearalizer.throttled.add(msg);
                return;
            }
            linearalizer.isExecutingRequest.set(true);
        }

        ChannelImapResponseWriter writer = new ChannelImapResponseWriter(ctx.channel(), session);
        ImapResponseComposerImpl response = new ImapResponseComposerImpl(writer);
        writer.setFlushCallback(response::flush);
        ImapMessage message = (ImapMessage) msg;

        beforeIDLEUponProcessing(ctx);
        ResponseEncoder responseEncoder = new ResponseEncoder(encoder, response);
        Disposable disposable = reactiveThrottler.throttle(processor.processReactive(message, responseEncoder, session)
                .doOnEach(Throwing.consumer(signal -> {
                    if (session.getState() == ImapSessionState.LOGOUT) {
                        // Make sure we close the channel after all the buffers were flushed out
                        Channel channel = ctx.channel();
                        if (channel.isActive()) {
                            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                    if (signal.isOnComplete()) {
                        IOException failure = responseEncoder.getFailure();
                        if (failure != null) {
                            try (Closeable mdc = ReactorUtils.retrieveMDCBuilder(signal).build()) {
                                LOGGER.info(failure.getMessage());
                                LOGGER.debug("Failed to write {}", message, failure);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            ctx.fireExceptionCaught(failure);
                        }
                    }
                    Object waitingMessage;
                    synchronized (linearalizer) {
                        linearalizer.isExecutingRequest.set(false);
                        waitingMessage = linearalizer.throttled.poll();
                    }
                    if (signal.isOnComplete() || signal.isOnError()) {
                        afterIDLEUponProcessing(ctx);
                    }
                    if (signal.hasError()) {
                        ctx.fireExceptionCaught(signal.getThrowable());
                    }
                    disposableAttribute.set(null);
                    response.flush();
                    ctx.fireChannelReadComplete();
                    if (signal.isOnComplete() || signal.isOnError()) {
                        if (waitingMessage != null && signal.isOnComplete()) {
                            ctx.channel().eventLoop().execute(
                                () -> channelRead(ctx, waitingMessage));
                        }
                    }
                }))
                .contextWrite(ReactorUtils.context("imap", mdc(session))), message,
                runnable -> ctx.channel().eventLoop().execute(runnable))
            // Manage throttling errors
            .doOnError(ctx::fireExceptionCaught)
            .doFinally(Throwing.consumer(any -> {
                if (message instanceof Closeable) {
                    ((Closeable) message).close();
                }
            }))
            .subscribe();
        disposableAttribute.set(disposable);
    }

    private void beforeIDLEUponProcessing(ChannelHandlerContext ctx) {
        if (!ignoreIDLEUponProcessing) {
            try {
                ctx.pipeline().addBefore(NettyConstants.CORE_HANDLER, NettyConstants.HEARTBEAT_HANDLER, heartbeatHandler);
            } catch (IllegalArgumentException e) {
                LOGGER.info("heartbeat handler is already part of this pipeline", e);
            }
        }
    }

    private void afterIDLEUponProcessing(ChannelHandlerContext ctx) {
        if (!ignoreIDLEUponProcessing) {
            try {
                ctx.pipeline().remove(NettyConstants.HEARTBEAT_HANDLER);
            } catch (NoSuchElementException e) {
                LOGGER.info("Heartbeat handler was concurrently removed");
            }
        }
    }
}
