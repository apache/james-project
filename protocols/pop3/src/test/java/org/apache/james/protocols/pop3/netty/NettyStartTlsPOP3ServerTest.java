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
package org.apache.james.protocols.pop3.netty;

import java.net.InetSocketAddress;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.pop3.AbstractStartTlsPOP3ServerTest;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;

public class NettyStartTlsPOP3ServerTest extends AbstractStartTlsPOP3ServerTest {

    private HashedWheelTimer hashedWheelTimer;

    @Before
    public void setup() {
        hashedWheelTimer = new HashedWheelTimer();
    }

    @After
    public void teardown() {
        hashedWheelTimer.stop();
    }


    @Override
    protected ProtocolServer createServer(Protocol protocol, InetSocketAddress address, Encryption enc) {
        NettyServer server = new NettyServer.Factory(hashedWheelTimer)
                .protocol(protocol)
                .secure(enc)
                .build();
        server.setListenAddresses(address);
        
        return server;
    }

}
