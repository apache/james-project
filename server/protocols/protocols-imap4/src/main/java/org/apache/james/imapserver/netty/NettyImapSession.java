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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
import reactor.core.publisher.Mono;

public class NettyImapSession implements ImapSession, NettyConstants {
    private static final int BUFFER_SIZE = 2048;

    private ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;
    private SelectedMailbox selectedMailbox;
    private final Map<String, Object> attributesByKey = new HashMap<>();
    private final Encryption secure;
    private final boolean compress;
    private final Channel channel;
    private final boolean requiredSSL;
    private final boolean plainAuthEnabled;
    private final SessionId sessionId;
    private boolean needsCommandInjectionDetection;
    private Optional<OidcSASLConfiguration> oidcSASLConfiguration;
    private boolean supportsOAuth;
    private MailboxSession mailboxSession = null;

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
        return closeMailbox()
            .then(Mono.fromRunnable(() -> selectedMailbox = mailbox));
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
        return this.selectedMailbox;
    }

    @Override
    public ImapSessionState getState() {
        return this.state;
    }

    private Mono<Void> closeMailbox() {
        if (selectedMailbox != null) {
            return selectedMailbox.deselect()
                .then(Mono.fromRunnable(() -> selectedMailbox = null));
        }
        return Mono.empty();
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
    public boolean startTLS() {
        if (!supportStartTLS()) {
            return false;
        }
        channel.config().setAutoRead(false);

        channel.pipeline().addFirst(SSL_HANDLER, secure.sslHandler());
        stopDetectingCommandInjection();
        channel.config().setAutoRead(true);

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
        return secure != null && secure.supportsEncryption();
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

        channel.eventLoop().execute(() -> {
            channel.config().setAutoRead(false);
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

            channel.config().setAutoRead(true);
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
    public Optional<OidcSASLConfiguration> oidcSaslConfiguration() {
        return oidcSASLConfiguration;
    }

    @Override
    public boolean isTLSActive() {
        return channel.pipeline().get(SSL_HANDLER) != null;
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
}
