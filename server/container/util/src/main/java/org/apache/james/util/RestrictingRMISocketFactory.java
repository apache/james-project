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
package org.apache.james.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link RMISocketFactory} implementation which allow to bind JMX to a specific
 * IP
 */
public class RestrictingRMISocketFactory extends RMISocketFactory {

    private final String address;

    private final List<ServerSocket> sockets = new ArrayList<>();

    public RestrictingRMISocketFactory(String address) {
        this.address = address;
    }

    public RestrictingRMISocketFactory() {
        this("localhost");
    }

    /**
     * Create a {@link ServerSocket} which is bound to an specific address and
     * the given port. The address can be specified by the System Property
     * james.jmx.address. If none is given it will use localhost
     */
    public ServerSocket createServerSocket(int port) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(address, port));
        sockets.add(socket);
        return socket;
    }

    /**
     * Create a new {@link Socket} for the given host and port
     */
    public Socket createSocket(String host, int port) throws IOException {
        return new Socket(host, port);
    }

    public List<ServerSocket> getSockets() {
        return sockets;
    }
}
