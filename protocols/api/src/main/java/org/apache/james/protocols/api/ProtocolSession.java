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

package org.apache.james.protocols.api;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.handler.LineHandler;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Session for a protocol. Every new connection generates a new session
 */
public interface ProtocolSession {
   
    enum State {
        Connection,
        Transaction
    }

    class AttachmentKey<T> {
        public static <U> AttachmentKey<U> of(String value, Class<U> type) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(value), "An attachment key should not be empty or null");

            return new AttachmentKey<>(value, type);
        }

        private final String value;
        private final Class<T> type;

        private AttachmentKey(String value, Class<T> type) {
            this.value = value;
            this.type = type;
        }

        public String asString() {
            return value;
        }

        public Optional<T> convert(Object object) {
            return Optional.ofNullable(object)
                .filter(type::isInstance)
                .map(type::cast);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof AttachmentKey) {
                AttachmentKey<?> that = (AttachmentKey<?>) o;

                return Objects.equals(this.value, that.value)
                    && Objects.equals(this.type, that.type);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value, type);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .add("type", type.getName())
                .toString();
        }
    }

    /**
     * Store the given value with the given key in the specified {@link State}.
     * 
     * @param key the key under which the value should get stored
     * @param value the value which will get stored under the given key
     * @param state the {@link State} to which the mapping belongs
     * @return oldValue the value which was stored before for this key or <code>null</code> if non was stored before.
     */
    <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state);

    /**
     * Remove a value stored for the given key in the specified {@link State}.
     *
     * @param key the key under which the value should get stored
     * @param state the {@link State} to which the mapping belongs
     * @return oldValue the value which was stored before for this key or <code>null</code> if non was stored before.
     */
    <T> Optional<T> removeAttachment(AttachmentKey<T> key,State state);

    /**
     * Return the value which is stored for the given key in the specified {@link State} or <code>null</code> if non was stored before.
     * 
     * @param key the key under which the value should be searched
     * @param state the {@link State} in which the value was stored for the key
     * @return value the stored value for the key
     */
    <T> Optional<T> getAttachment(AttachmentKey<T> key, State state);
    
    
    /**
     * Return Map which can be used to store objects within a session
     * 
     * @return state
     * @deprecated use {@link #setAttachment(AttachmentKey, Object, State)}
     */
    @Deprecated
    Map<AttachmentKey<?>, Object> getState();
    
    
    /**
     * Returns Map that consists of the state of the {@link ProtocolSession} per connection
     *
     * @return map of the current {@link ProtocolSession} state per connection
     * @deprecated use {@link #getAttachment(AttachmentKey, State)}
     */
    @Deprecated
    Map<AttachmentKey<?>, Object> getConnectionState();

    
    /**
     * Reset the state
     */
    void resetState();

    
    /**
     * Return the {@link InetSocketAddress} of the remote peer
     */
    InetSocketAddress getRemoteAddress();

    /**
     * Return the {@link InetSocketAddress} of the local bound address
     */
    InetSocketAddress getLocalAddress();
    
    /**
     * Return the ID for the session
     */
    String getSessionID();

    /**
     * Define a response object to be used as reply for a too long input line
     * 
     * @return Response or null if no response should be written before closing the connection
     */
    Response newLineTooLongResponse();

    /**
     * Define a response object to be used as reply during a fatal error.
     * Connection will be closed after this response.
     * 
     * @return Response or null if no response should be written before closing the connection
     */
    Response newFatalErrorResponse();

    Response newCommandNotFoundErrorResponse();
    
    /**
     * Returns the user name associated with this interaction.
     *
     * @return the user name
     */
    Username getUsername();

    /**
     * Sets the user name associated with this interaction.
     *
     * @param username the user name
     */
    void setUsername(Username username);

    /**
     * Return true if StartTLS is supported by the configuration
     * 
     * @return supported
     */
    boolean isStartTLSSupported();
    
    /**
     * Return true if the starttls was started
     */
    boolean isTLSStarted();
    
    /**
     * Return the {@link ProtocolConfiguration}
     */
    ProtocolConfiguration getConfiguration();
    
    /**
     * Return the {@link Charset} which is used by the {@link ProtocolSession}
     */
    Charset getCharset();
    
    /**
     * Return the line delimiter which is used
     * 
     * @return delimiter
     */
    String getLineDelimiter();
    
    /**
     * Put a new line handler in the chain
     */
    <T extends ProtocolSession> void pushLineHandler(LineHandler<T> overrideCommandHandler);
    
    /**
     * Pop the last command handler 
     */
    void popLineHandler();
    
    /**
     * Return the size of the pushed {@link LineHandler}
     * @return size of the pushed line handler
     */
    int getPushedLineHandlerCount();

}
