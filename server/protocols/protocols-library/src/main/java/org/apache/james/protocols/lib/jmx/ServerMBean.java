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
package org.apache.james.protocols.lib.jmx;

/**
 * JMX MBean interface for servers
 */
public interface ServerMBean {

    /**
     * Return the maximum allowed concurrent connections for the server
     */
    int getMaximumConcurrentConnections();

    /**
     * Return the current connection count
     */
    int getCurrentConnections();

    /**
     * Return the count of handled connections till startup
     */
    long getHandledConnections();

    /**
     * Return true if the server is enabled
     */
    boolean isEnabled();

    /**
     * Return true if startTLS is supported by the server
     */
    boolean getStartTLSSupported();

    String[] getBoundAddresses();

    /**
     * Return the socket type of the server. Which can either be plain or secure
     * 
     */
    String getSocketType();

    /**
     * Return the service type of the server
     * 
     */
    String getServiceType();

    /**
     * Return true if the server is started, which basicly means it is bound to
     * a address and accept connections
     */
    boolean isStarted();

    /**
     * Start the server
     */
    boolean start();

    /**
     * Stop the server
     */
    boolean stop();

    /**
     * Return the timeout in seconds
     */
    int getTimeout();
}
