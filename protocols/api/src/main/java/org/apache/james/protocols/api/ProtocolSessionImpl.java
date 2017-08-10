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
import org.apache.james.protocols.api.logger.ContextualLogger;
import org.slf4j.Logger;

/**
 * Basic implementation of {@link ProtocolSession}
 * 
 * 
 */
public class ProtocolSessionImpl implements ProtocolSession {

    private final Logger pLog;
    private final ProtocolTransport transport;
    private final Map<String, Object> connectionState;
    private final Map<String, Object> sessionState;
    private String user;
    protected final ProtocolConfiguration config;
    private final static Charset CHARSET = Charset.forName("US-ASCII");
    private final static String DELIMITER = "\r\n";
    
    public ProtocolSessionImpl(Logger logger, ProtocolTransport transport, ProtocolConfiguration config) {
        this.transport = transport;
        this.pLog = new ContextualLogger(this, logger);
        this.connectionState = new HashMap<>();
        this.sessionState = new HashMap<>();
        this.config = config;

    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getLocalAddress()
     */
    public InetSocketAddress getLocalAddress() {
        return transport.getLocalAddress();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getRemoteAddress()
     */
    public InetSocketAddress getRemoteAddress() {
        return transport.getRemoteAddress();
    }

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getUser()
     */
    public String getUser() {
        return user;
    }

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#setUser(java.lang.String)
     */
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

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#isStartTLSSupported()
     */
    public boolean isStartTLSSupported() {
        return transport.isStartTLSSupported();
    }

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#isTLSStarted()
     */
    public boolean isTLSStarted() {
        return transport.isTLSStarted();
    }


    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getLogger()
     */
    public Logger getLogger() {
        return pLog;
    }
    

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getSessionID()
     */
    public String getSessionID() {
        return transport.getId();
    }
    
    
    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getConnectionState()
     */
    public Map<String, Object> getConnectionState() {
        return connectionState;
    }

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getState()
     */
    public Map<String, Object> getState() {
        return sessionState;
    }

    /**
     * This implementation just returns <code>null</code>. Sub-classes should
     * overwrite this if needed
     */
    public Response newLineTooLongResponse() {
        return null;
    }

    /**
     * This implementation just returns <code>null</code>. Sub-classes should
     * overwrite this if needed
     */
    public Response newFatalErrorResponse() {
        return null;
    }

    /**
     * This implementation just clears the sessions state. Sub-classes should
     * overwrite this if needed
     */
    public void resetState() {
        sessionState.clear();
    }

    /**
     * @see org.apache.james.protocols.api.ProtocolSession#getConfiguration()
     */
    public ProtocolConfiguration getConfiguration() {
        return config;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#setAttachment(java.lang.String, java.lang.Object, org.apache.james.protocols.api.ProtocolSession.State)
     */
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

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getAttachment(java.lang.String, org.apache.james.protocols.api.ProtocolSession.State)
     */
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
    public Charset getCharset() {
        return CHARSET;
    }

    /**
     * Returns "\r\n";
     */
    public String getLineDelimiter() {
        return DELIMITER;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#popLineHandler()
     */
    public void popLineHandler() {
        transport.popLineHandler();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getPushedLineHandlerCount()
     */
    public int getPushedLineHandlerCount() {
        return transport.getPushedLineHandlerCount();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#pushLineHandler(org.apache.james.protocols.api.handler.LineHandler)
     */
    public <T extends ProtocolSession> void pushLineHandler(LineHandler<T> overrideCommandHandler) {
        transport.pushLineHandler(overrideCommandHandler, this);
    }

}
