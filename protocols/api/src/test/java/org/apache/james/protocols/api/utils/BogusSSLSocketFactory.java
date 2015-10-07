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
package org.apache.james.protocols.api.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

public class BogusSSLSocketFactory extends SSLSocketFactory {

    private static final SSLSocketFactory FACTORY = BogusSslContextFactory.getClientContext().getSocketFactory();
    
    
    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return FACTORY.createSocket(s, host, port, autoClose);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return FACTORY.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return FACTORY.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return FACTORY.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port) throws IOException {
        return FACTORY.createSocket(address, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localhost, int localport) throws IOException, UnknownHostException {
        return FACTORY.createSocket(host, port, localhost, localport);

    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localaddress, int localport) throws IOException {
        return FACTORY.createSocket(address, port, localaddress, localport);
    }

}
