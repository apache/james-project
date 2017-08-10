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

import org.apache.james.protocols.api.handler.LineHandler;
import org.slf4j.Logger;

/**
 * Session for a protocol. Every new connection generates a new session
 * 
 *
 */
public interface ProtocolSession {
   
    enum State {
        Connection,
        Transaction
    }
    /**
     * Gets the context sensitive log for this session.
     * @return log, not null
     */
    Logger getLogger();
    
    
    /**
     * Store the given value with the given key in the specified {@link State}. If you want to remove a value you need to use <code>null</code> as value
     * 
     * @param key the key under which the value should get stored
     * @param value the value which will get stored under the given key or <code>null</code> if you want to remove any value which is stored under the key
     * @param state the {@link State} to which the mapping belongs
     * @return oldValue the value which was stored before for this key or <code>null</code> if non was stored before.
     */
    Object setAttachment(String key, Object value, State state);
    
    /**
     * Return the value which is stored for the given key in the specified {@link State} or <code>null</code> if non was stored before.
     * 
     * @param key the key under which the value should be searched
     * @param state the {@link State} in which the value was stored for the key
     * @return value the stored value for the key
     */
    Object getAttachment(String key, State state);
    
    
    /**
     * Return Map which can be used to store objects within a session
     * 
     * @return state
     * @deprecated use {@link #setAttachment(String, Object, State)}
     */
    @Deprecated
    Map<String, Object> getState();
    
    
    /**
     * Returns Map that consists of the state of the {@link ProtocolSession} per connection
     *
     * @return map of the current {@link ProtocolSession} state per connection
     * @deprecated use {@link #getAttachment(String, State)}
     */
    @Deprecated
    Map<String,Object> getConnectionState();

    
    /**
     * Reset the state
     */
    void resetState();

    
    /**
     * Return the {@link InetSocketAddress} of the remote peer
     * 
     * @return address
     */
    InetSocketAddress getRemoteAddress();

    /**
     * Return the {@link InetSocketAddress} of the local bound address
     * 
     * @return local
     */
    InetSocketAddress getLocalAddress();
    
    /**
     * Return the ID for the session
     * 
     * @return id
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
    
    /**
     * Returns the user name associated with this interaction.
     *
     * @return the user name
     */
    String getUser();

    /**
     * Sets the user name associated with this interaction.
     *
     * @param user the user name
     */
    void setUser(String user);

    /**
     * Return true if StartTLS is supported by the configuration
     * 
     * @return supported
     */
    boolean isStartTLSSupported();
    
    /**
     * Return true if the starttls was started
     * 
     * @return true
     */
    boolean isTLSStarted();
    
    /**
     * Return the {@link ProtocolConfiguration} 
     * 
     * @return config
     */
    ProtocolConfiguration getConfiguration();
    
    /**
     * Return the {@link Charset} which is used by the {@link ProtocolSession}
     * 
     * @return charset
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
     * @param overrideCommandHandler
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
