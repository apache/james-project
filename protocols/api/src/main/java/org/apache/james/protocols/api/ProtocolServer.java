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
import java.util.List;

/**
 * A {@link ProtocolServer} accept inbound traffic and handle it. Basically the protocols API can be used to handle every "line based" protocol in an easy fashion
 *
 */
public interface ProtocolServer {

    /**
     * Start the server
     * 
     * @throws Exception 
     * 
     */
    void bind() throws Exception;
    
    /**
     * Stop the server
     */
    void unbind();
    
    /**
     * return true if the server is bound 
     * 
     * @return bound
     */
    boolean isBound();

    /**
     * Return the read/write timeout in seconds for the socket.
     * @return the timeout
     */
    int  getTimeout();
    
    /**
     * Return the backlog for the socket
     * 
     * @return backlog
     */
    int getBacklog();
    
    /**
     * Return the ips on which the server listen for connections
     * 
     * @return ips
     */
    List<InetSocketAddress> getListenAddresses();
}
