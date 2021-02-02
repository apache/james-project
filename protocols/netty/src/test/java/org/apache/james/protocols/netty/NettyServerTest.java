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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import javax.net.ssl.SSLContext;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NettyServerTest {
    private HashedWheelTimer hashedWheelTimer;

    @BeforeEach
    void setup() {
        hashedWheelTimer = new HashedWheelTimer();
    }

    @AfterEach
    void teardown() {
        hashedWheelTimer.stop();
    }

    @Test
    void protocolShouldThrowWhenProtocolIsNull() {
        assertThatThrownBy(() -> new NettyServer.Factory(hashedWheelTimer).protocol(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowWhenProtocolIsNotGiven() {
        assertThatThrownBy(() -> new NettyServer.Factory(hashedWheelTimer)
            .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldWorkWhenProtocolIsGiven() {
        Protocol protocol = mock(Protocol.class);
        new NettyServer.Factory(hashedWheelTimer)
            .protocol(protocol)
            .build();
    }

    @Test
    void buildShouldWorkWhenEverythingIsGiven() throws Exception {
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
