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

import org.apache.james.protocols.api.handler.LineHandler;

/**
 * ProtocolTransport is used by each ProtocolSession to communicate with the underlying transport.
 * Transport implementations will provide their own implementation of the transport.
 * 
 * Every new connection gets a new instance of {@link ProtocolTransport}. So its not shared between connections.
 */
public interface ProtocolTransport {

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
     * Return the unique id. The id MUST NOT be 100 % unique for ever. It just should just not have the same
     * id when having concurrent connections
     * 
     * @return id
     */
    String getId();

    /**
     * Return <code>true</code> if <code>TLS</code> encryption is active
     * 
     * @return tlsStarted
     */
    boolean isTLSStarted();

    /**
     * Return <code>true</code> if <code>STARTTLS</code> is supported by this {@link ProtocolTransport}
     * 
     * @return tlsSupprted
     */
    boolean isStartTLSSupported();
    
    /**
     * Write the {@link Response} to the {@link ProtocolTransport} which will forward it to the connected
     * peer
     * 
     * @param response
     * @param session
     */
    void writeResponse(Response response, ProtocolSession session);

    /**
     * Pop a {@link LineHandler} of the stack
     */
    void popLineHandler();

    /**
     * Push a {@link LineHandler} in.
     * 
     * @param overrideCommandHandler
     * @param session
     */
    void pushLineHandler(LineHandler<? extends ProtocolSession> overrideCommandHandler, ProtocolSession session);

    /**
     * Return the count of pushed {@link LineHandler}'s
     * 
     * @return lineCount
     */
    int getPushedLineHandlerCount();
    
    
    /**
     * Set the {@link ProtocolTransport} readable or not. If its not readable then no new lines should get processed 
     * 
     * @param readable
     */
    void setReadable(boolean readable);

    /**
     * Return <code>true</code> if the channel is readable
     * 
     * @return
     */
    boolean isReadable();
}
