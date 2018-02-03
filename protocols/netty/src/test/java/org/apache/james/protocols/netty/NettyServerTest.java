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

package org.apache.james.protocols.netty;

import static org.mockito.Mockito.mock;

import javax.net.ssl.SSLContext;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NettyServerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private HashedWheelTimer hashedWheelTimer;

    @Before
    public void setup() {
        hashedWheelTimer = new HashedWheelTimer();
    }

    @After
    public void teardown() {
        hashedWheelTimer.stop();
    }

    @Test
    public void protocolShouldThrowWhenProtocolIsNull() {
        expectedException.expect(NullPointerException.class);
        new NettyServer.Factory(hashedWheelTimer).protocol(null);
    }

    @Test
    public void buildShouldThrowWhenProtocolIsNotGiven() {
        expectedException.expect(IllegalStateException.class);
        new NettyServer.Factory(hashedWheelTimer)
            .build();
    }

    @Test
    public void buildShouldWorkWhenProtocolIsGiven() {
        Protocol protocol = mock(Protocol.class);
        new NettyServer.Factory(hashedWheelTimer)
            .protocol(protocol)
            .build();
    }

    @Test
    public void buildShouldWorkWhenEverythingIsGiven() throws Exception {
        Protocol protocol = mock(Protocol.class);
        Encryption encryption = Encryption.createStartTls(SSLContext.getDefault());
        ChannelHandlerFactory channelHandlerFactory = mock(ChannelHandlerFactory.class);
        new NettyServer.Factory(hashedWheelTimer)
            .protocol(protocol)
            .secure(encryption)
            .frameHandlerFactory(channelHandlerFactory)
            .build();
    }
}
