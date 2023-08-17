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
package org.apache.james.protocols.lmtp.netty;

import java.net.InetSocketAddress;

import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.lmtp.AbstractLMTPSServerTest;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.netty.NettyServer;

public class NettyLMTPSServerTest extends AbstractLMTPSServerTest {

    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int RANDOM_PORT = 0;

    @Override
    protected ProtocolServer createServer(Protocol protocol) {
        NettyServer server = new NettyServer.Factory()
                .protocol(protocol)
                .secure(Encryption.createTls(BogusSslContextFactory.getServerContext()))
                .build();
        server.setListenAddresses(new InetSocketAddress(LOCALHOST_IP, RANDOM_PORT));
        return server;
    }
    
}
