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

package org.apache.james.imap.api.process;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLSession;

import org.apache.commons.text.RandomStringGenerator;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.api.OidcSASLConfiguration;

import reactor.core.publisher.Mono;

/**
 * Encapsulates all state held for an ongoing Imap session, which commences when
 * a client first establishes a connection to the Imap server, and continues
 * until that connection is closed.
 * 
 * @version $Revision: 109034 $
 */
public interface ImapSession extends CommandDetectionSession {
    class SessionId {
        private static final RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder()
            .withinRange('a', 'z')
            .build();
        private static final int LENGTH = 12;

        public static SessionId generate() {
            return new SessionId("SID-" + RANDOM_STRING_GENERATOR.generate(LENGTH));
        }

        private final String value;

        private SessionId(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SessionId) {
                SessionId sessionId = (SessionId) o;

                return Objects.equals(this.value, sessionId.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return asString();
        }
    }

    String MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY = "org.apache.james.api.imap.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY";

    /**
     * @return a unique identifier for this session.
     *
     * One of its usage is log correlation.
     */
    SessionId sessionId();

    default void executeSafely(Runnable runnable) {
        runnable.run();
    }

    /**
     * Logs out the session. Marks the connection for closure;
     */
    Mono<Void> logout();

    /**
     * Allows implementation to apply back pressure on heavy senders.
     *
     * Return true if the sender needs to be throttled.
     * Return false if backpressure do not need to be applied.
     * @param restoreBackpressure will be called to restore backpressure to its current state when backpressure
     *                            is no longer needed.
     */
    default boolean backpressureNeeded(Runnable restoreBackpressure) {
        // Naive implementation: never backpressure
        return false;
    }

    /**
     * Gets the current client state.
     * 
     * @return Returns the current state of this session.
     */
    ImapSessionState getState();

    /**
     * Moves the session into {@link ImapSessionState#AUTHENTICATED} state.
     */
    void authenticated();

    /**
     * Moves this session into {@link ImapSessionState#SELECTED} state and sets
     * the supplied mailbox to be the currently selected mailbox.
     * 
     * @param mailbox
     *            The selected mailbox.
     */
    Mono<Void> selected(SelectedMailbox mailbox);

    /**
     * Moves the session out of {@link ImapSessionState#SELECTED} state and back
     * into {@link ImapSessionState#AUTHENTICATED} state. The selected mailbox
     * is cleared.
     */
    Mono<Void> deselect();

    /**
     * Provides the selected mailbox for this session, or <code>null</code> if
     * this session is not in {@link ImapSessionState#SELECTED} state.
     * 
     * @return the currently selected mailbox.
     */
    SelectedMailbox getSelected();

    /**
     * Gets an attribute of this session by name. Implementations should ensure
     * that access is thread safe.
     * 
     * @param key
     *            name of the key, not null
     * @return <code>Object</code> value or null if this attribute has unvalued
     */
    Object getAttribute(String key);

    /**
     * Sets an attribute of this session by name. Implementations should ensure
     * that access is thread safe.
     * 
     * @param key
     *            name of the key, not null
     * @param value
     *            <code>Object</code> value or null to set this attribute as
     *            unvalued
     */
    void setAttribute(String key, Object value);

    /**
     * Start TLS encryption of the session after the next response was written.
     * So you must make sure the next response will get send in clear text
     * 
     * @return true if the encryption of the session was successfully
     */
    boolean startTLS(Runnable runnable);

    /**
     * Return true if the session is bound to a TLS encrypted socket.
     * 
     * @return tlsActive
     */
    boolean isTLSActive();
 
    /**
     * Support startTLS ?
     * 
     * @return true if startTLS is supported
     */
    boolean supportStartTLS();

    /**
     * Return the {@link SSLSession} of this protocol session. Empty if it does not use SSL/TLS.
     * 
     * @return SSLSession
     */
    Optional<SSLSession> getSSLSession();
    
    /**
     * Return true if compression is active
     * 
     * @return compActive
     */
    boolean isCompressionActive();

    /**
     * Return true if compression is supported. This is related to COMPRESS extension.
     * See http://www.ietf.org/rfc/rfc4978.txt
     * 
     * @return compressSupport
     */
    boolean isCompressionSupported();

    /**
     * Start the compression
     * 
     * @return success
     */
    boolean startCompression(Runnable runnable);

    /**
     * Push in a new {@link ImapLineHandler} which is called for the next line received
     */
    void pushLineHandler(ImapLineHandler lineHandler);

    /**
     * Pop the current {@link ImapLineHandler}
     */
    void popLineHandler();
    
    /**
     * Return true if multiple namespaces are supported
     */
    boolean supportMultipleNamespaces();
    
    /**
     * Return true if SSL is required when Authenticating
     */
    boolean isSSLRequired();

    /**
     * Return true if the login / authentication via plain username / password is
     * enabled
     */
    boolean isPlainAuthEnabled();

    boolean supportsOAuth();

    /**
     * Return the {@link InetSocketAddress} of the remote peer
     */
    InetSocketAddress getRemoteAddress();

    Optional<OidcSASLConfiguration> oidcSaslConfiguration();

    default void setMailboxSession(MailboxSession mailboxSession) {
        setAttribute(MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY, mailboxSession);
    }

    default MailboxSession getMailboxSession() {
        return (MailboxSession) getAttribute(MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY);
    }

    default Username getUserName() {
        return Optional.ofNullable(getMailboxSession())
            .map(MailboxSession::getUser)
            .orElse(null);
    }

    default boolean isPlainAuthDisallowed() {
        return !isPlainAuthEnabled() || isAuthenticatingNonEncryptedWhenRequiredSSL();
    }

    default boolean isAuthenticatingNonEncryptedWhenRequiredSSL() {
        return isSSLRequired() && !isTLSActive();
    }

    void schedule(Runnable runnable, Duration waitDelay);

}
