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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.Literal;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.netty.LineHandlerAware;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslHandler;
import reactor.core.publisher.Mono;

public class NettyImapSession implements ImapSession, NettyConstants {
    private final Map<String, Object> attributesByKey = new HashMap<>();
    private final Encryption secure;
    private final boolean compress;
    private final Channel channel;
    private final boolean requiredSSL;
    private final boolean plainAuthEnabled;
    private final SessionId sessionId;
    private final boolean supportsOAuth;
    private final Optional<OidcSASLConfiguration> oidcSASLConfiguration;

    private volatile ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;
    private final AtomicReference<SelectedMailbox> selectedMailbox = new AtomicReference<>();
    private volatile boolean needsCommandInjectionDetection;
    private volatile MailboxSession mailboxSession = null;

    public NettyImapSession(Channel channel, Encryption secure, boolean compress, boolean requiredSSL, boolean plainAuthEnabled, SessionId sessionId,
                            Optional<OidcSASLConfiguration> oidcSASLConfiguration) {
        this.channel = channel;
        this.secure = secure;
        this.compress = compress;
        this.requiredSSL = requiredSSL;
        this.plainAuthEnabled = plainAuthEnabled;
        this.sessionId = sessionId;
        this.needsCommandInjectionDetection = true;
        this.oidcSASLConfiguration = oidcSASLConfiguration;
        this.supportsOAuth = oidcSASLConfiguration.isPresent();
    }

    @Override
    public boolean needsCommandInjectionDetection() {
        return needsCommandInjectionDetection;
    }

    @Override
    public void startDetectingCommandInjection() {
        needsCommandInjectionDetection = true;
    }

    @Override
    public void stopDetectingCommandInjection() {
        needsCommandInjectionDetection = false;
    }

    @Override
    public SessionId sessionId() {
        return sessionId;
    }

    @Override
    public void executeSafely(Runnable runnable) {
        channel.eventLoop().execute(() -> {
            channel.config().setAutoRead(false);
            runnable.run();

            channel.config().setAutoRead(true);
        });
    }

    @Override
    public Mono<Void> logout() {
        return closeMailbox()
            .then(Mono.fromRunnable(() -> state = ImapSessionState.LOGOUT));
    }

    @Override
    public void authenticated() {
        this.state = ImapSessionState.AUTHENTICATED;
    }

    @Override
    public Mono<Void> deselect() {
        this.state = ImapSessionState.AUTHENTICATED;
        return closeMailbox();
    }

    @Override
    public Mono<Void> selected(SelectedMailbox mailbox) {
        this.state = ImapSessionState.SELECTED;
        return Mono.fromCallable(() -> Optional.ofNullable(selectedMailbox.getAndSet(mailbox)))
            .flatMap(maybeMailbox -> maybeMailbox.map(SelectedMailbox::deselect)
                .orElse(Mono.empty()));
    }

    @Override
    public MailboxSession getMailboxSession() {
        return mailboxSession;
    }

    @Override
    public void setMailboxSession(MailboxSession mailboxSession) {
        this.mailboxSession = mailboxSession;
    }

    @Override
    public SelectedMailbox getSelected() {
        return this.selectedMailbox.get();
    }

    @Override
    public ImapSessionState getState() {
        return this.state;
    }

    private Mono<Void> closeMailbox() {
        return closeMailbox(selectedMailbox.getAndSet(null));
    }

    private Mono<Void> closeMailbox(SelectedMailbox value) {
        return Optional.ofNullable(value)
            .map(s -> s.deselect()
                .then(Mono.fromRunnable(() -> selectedMailbox.set(null))))
            .orElse(Mono.empty())
            .then();
    }

    @Override
    public Object getAttribute(String key) {
        return attributesByKey.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributesByKey.remove(key);
        } else {
            attributesByKey.put(key, value);
        }
    }

    @Override
    public boolean startTLS(Runnable runnable) {
        if (!supportStartTLS()) {
            return false;
        }
        executeSafely(() -> {
            runnable.run();
            channel.pipeline().addFirst(SSL_HANDLER, secure.sslHandler());
            stopDetectingCommandInjection();
        });

        return true;
    }

    public static class EventLoopImapResponseWriter implements ImapResponseWriter {
        private final Channel channel;

        public EventLoopImapResponseWriter(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void write(byte[] buffer) {
            if (channel.isActive()) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(buffer));
            }
        }

        @Override
        public void write(Literal literal) {
            throw new NotImplementedException();
        }
    }


    @Override
    public boolean supportStartTLS() {
        return secure != null && secure.supportsEncryption() && !isTLSActive();
    }

    @Override
    public boolean isCompressionSupported() {
        return compress;
    }

    @Override
    public boolean startCompression(Runnable runnable) {
        if (!isCompressionSupported()) {
            return false;
        }

        executeSafely(() -> {
            runnable.run();
            ZlibDecoder decoder = new JZlibDecoder(ZlibWrapper.NONE);
            ZlibEncoder encoder = new JZlibEncoder(ZlibWrapper.NONE, 5);

            // Check if we have the SslHandler in the pipeline already
            // if so we need to move the compress encoder and decoder
            // behind it in the chain
            // See JAMES-1186
            if (channel.pipeline().get(SSL_HANDLER) == null) {
                channel.pipeline().addFirst(ZLIB_DECODER, decoder);
                channel.pipeline().addFirst(ZLIB_ENCODER, encoder);
            } else {
                channel.pipeline().addAfter(SSL_HANDLER, ZLIB_DECODER, decoder);
                channel.pipeline().addAfter(SSL_HANDLER, ZLIB_ENCODER, encoder);
            }
        });

        return true;
    }

    @Override
    public void pushLineHandler(ImapLineHandler lineHandler) {
        LineHandlerAware handler = (LineHandlerAware) channel.pipeline().get(REQUEST_DECODER);
        handler.pushLineHandler(new ImapLineHandlerAdapter(this, lineHandler));
    }

    @Override
    public void popLineHandler() {
        LineHandlerAware handler = (LineHandlerAware) channel.pipeline().get(REQUEST_DECODER);
        handler.popLineHandler();
    }

    @Override
    public boolean isSSLRequired() {
        return requiredSSL;
    }

    @Override
    public boolean isPlainAuthEnabled() {
        return plainAuthEnabled;
    }

    @Override
    public boolean supportsOAuth() {
        return supportsOAuth;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public Optional<OidcSASLConfiguration> oidcSaslConfiguration() {
        return oidcSASLConfiguration;
    }

    @Override
    public boolean isTLSActive() {
        return channel.pipeline().get(SSL_HANDLER) != null;
    }

    @Override
    public Optional<SSLSession> getSSLSession() {
        return Optional.ofNullable(channel.pipeline().get(SSL_HANDLER))
            .map(SslHandler.class::cast)
            .map(SslHandler::engine)
            .map(SSLEngine::getSession);
    }

    @Override
    public boolean supportMultipleNamespaces() {
        return false;
    }

    @Override
    public boolean isCompressionActive() {
        return channel.pipeline().get(ZLIB_DECODER) != null;
    }

    @Override
    public void schedule(Runnable runnable, Duration waitDelay) {
        channel.eventLoop().schedule(runnable, waitDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean backpressureNeeded(Runnable restoreBackpressure) {
        boolean writable = channel.isWritable();
        if (!writable) {
            channel.attr(BACKPRESSURE_CALLBACK).set(restoreBackpressure);
            return true;
        }
        return false;
    }
}
