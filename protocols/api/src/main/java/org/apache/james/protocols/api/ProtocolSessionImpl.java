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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.handler.LineHandler;

import com.google.common.base.Preconditions;

/**
 * Basic implementation of {@link ProtocolSession}
 * 
 * 
 */
public class ProtocolSessionImpl implements ProtocolSession {
    private final ProtocolTransport transport;
    private final Map<AttachmentKey<?>, Object> connectionState;
    private final Map<AttachmentKey<?>, Object> sessionState;
    private Username username;
    protected final ProtocolConfiguration config;
    private static final Charset CHARSET = Charset.forName("US-ASCII");
    private static final String DELIMITER = "\r\n";
    
    public ProtocolSessionImpl(ProtocolTransport transport, ProtocolConfiguration config) {
        this.transport = transport;
        this.connectionState = new HashMap<>();
        this.sessionState = new HashMap<>();
        this.config = config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return transport.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return transport.getRemoteAddress();
    }

    @Override
    public Username getUsername() {
        return username;
    }

    @Override
    public void setUsername(Username username) {
        this.username = username;
    }

    /**
     * Return the wrapped {@link ProtocolTransport} which is used for this {@link ProtocolSession}
     */
    public ProtocolTransport getProtocolTransport() {
        return transport;
    }

    @Override
    public boolean isStartTLSSupported() {
        return transport.isStartTLSSupported();
    }

    @Override
    public boolean isTLSStarted() {
        return transport.isTLSStarted();
    }

    @Override
    public String getSessionID() {
        return transport.getId();
    }
    
    
    @Override
    public Map<AttachmentKey<?>, Object> getConnectionState() {
        return connectionState;
    }

    @Override
    public Map<AttachmentKey<?>, Object> getState() {
        return sessionState;
    }

    /**
     * This implementation just returns <code>null</code>. Sub-classes should
     * overwrite this if needed
     */
    @Override
    public Response newLineTooLongResponse() {
        return null;
    }

    /**
     * This implementation just returns <code>null</code>. Sub-classes should
     * overwrite this if needed
     */
    @Override
    public Response newFatalErrorResponse() {
        return null;
    }

    /**
     * This implementation just clears the sessions state. Sub-classes should
     * overwrite this if needed
     */
    @Override
    public void resetState() {
        sessionState.clear();
    }

    @Override
    public ProtocolConfiguration getConfiguration() {
        return config;
    }

    @Override
    public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
        Preconditions.checkNotNull(key, "key cannot be null");
        Preconditions.checkNotNull(value, "value cannot be null");

        if (state == State.Connection) {
            return key.convert(connectionState.put(key, value));
        } else {
            return key.convert(sessionState.put(key, value));
        }
    }

    @Override
    public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
        Preconditions.checkNotNull(key, "key cannot be null");

        if (state == State.Connection) {
            return key.convert(connectionState.remove(key));
        } else {
            return key.convert(sessionState.remove(key));
        }
    }

    @Override
    public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
        if (state == State.Connection) {
            return key.convert(connectionState.get(key));
        } else {
            return key.convert(sessionState.get(key));
        }
    }

    /**
     * Returns a Charset for US-ASCII
     */
    @Override
    public Charset getCharset() {
        return CHARSET;
    }

    /**
     * Returns "\r\n";
     */
    @Override
    public String getLineDelimiter() {
        return DELIMITER;
    }

    @Override
    public void popLineHandler() {
        transport.popLineHandler();
    }

    @Override
    public int getPushedLineHandlerCount() {
        return transport.getPushedLineHandlerCount();
    }

    @Override
    public <T extends ProtocolSession> void pushLineHandler(LineHandler<T> overrideCommandHandler) {
        transport.pushLineHandler(overrideCommandHandler, this);
    }

}
