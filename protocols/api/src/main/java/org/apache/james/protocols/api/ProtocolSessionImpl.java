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

import org.apache.james.protocols.api.handler.LineHandler;

/**
 * Basic implementation of {@link ProtocolSession}
 * 
 * 
 */
public class ProtocolSessionImpl implements ProtocolSession {
    private final ProtocolTransport transport;
    private final Map<String, Object> connectionState;
    private final Map<String, Object> sessionState;
    private String user;
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
    public String getUser() {
        return user;
    }

    @Override
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Return the wrapped {@link ProtocolTransport} which is used for this {@link ProtocolSession}
     * 
     * @return transport
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
    public Map<String, Object> getConnectionState() {
        return connectionState;
    }

    @Override
    public Map<String, Object> getState() {
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
    public Object setAttachment(String key, Object value, State state) {
        if (state == State.Connection) {
            if (value == null) {
                return connectionState.remove(key);
            } else {
                return connectionState.put(key, value);
            }
        } else {
            if (value == null) {
                return sessionState.remove(key);
            } else {
                return sessionState.put(key, value);
            }
        }
    }

    @Override
    public Object getAttachment(String key, State state) {
        if (state == State.Connection) {
            return connectionState.get(key);
        } else {
            return sessionState.get(key);
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
