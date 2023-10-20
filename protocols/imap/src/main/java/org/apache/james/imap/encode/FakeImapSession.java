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
package org.apache.james.imap.encode;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSession;

import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.util.concurrent.NamedThreadFactory;

import reactor.core.publisher.Mono;

public class FakeImapSession implements ImapSession {
    private static final int DEFAULT_SCHEDULED_POOL_CORE_SIZE = 5;
    private static final ThreadFactory THREAD_FACTORY = NamedThreadFactory.withClassName(FakeImapSession.class);
    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(DEFAULT_SCHEDULED_POOL_CORE_SIZE, THREAD_FACTORY);

    private ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;

    private SelectedMailbox selectedMailbox = null;

    private final Map<String, Object> attributesByKey;
    private final SessionId sessionId;

    public FakeImapSession() {
        this.sessionId = SessionId.generate();
        this.attributesByKey = new ConcurrentHashMap<>();
    }

    @Override
    public boolean needsCommandInjectionDetection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startDetectingCommandInjection() {

    }

    @Override
    public void stopDetectingCommandInjection() {

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
    public boolean startTLS(Runnable runnable) {
        return false;
    }

    @Override
    public boolean supportStartTLS() {
        return false;
    }

    @Override
    public Optional<SSLSession> getSSLSession() {
        return Optional.empty();
    }

    @Override
    public boolean isCompressionSupported() {
        return false;
    }

    @Override
    public boolean startCompression(Runnable runnable) {
        return false;
    }

    @Override
    public void pushLineHandler(ImapLineHandler lineHandler) {
    }

    @Override
    public void popLineHandler() {
        
    }

    @Override
    public boolean isSSLRequired() {
        return false;
    }

    @Override
    public boolean isPlainAuthEnabled() {
        return true;
    }

    @Override
    public boolean supportsOAuth() {
        return false;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public Optional<OidcSASLConfiguration> oidcSaslConfiguration() {
        return Optional.empty();
    }

    @Override
    public boolean isTLSActive() {
        return false;
    }

    @Override
    public boolean supportMultipleNamespaces() {
        return false;
    }

    @Override
    public boolean isCompressionActive() {
        return false;
    }

    @Override
    public void schedule(Runnable runnable, Duration waitDelay) {
       EXECUTOR_SERVICE.schedule(runnable, waitDelay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
