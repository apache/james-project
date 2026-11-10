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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolException;

class BoundedHAProxyMessageDecoderTest {
    private static final byte[] V2_SIGNATURE = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};

    @Test
    void shouldDecodeValidHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new BoundedHAProxyMessageDecoder());

        channel.writeInbound(Unpooled.copiedBuffer("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n", StandardCharsets.US_ASCII));

        HAProxyMessage message = channel.readInbound();
        assertThat(message.sourceAddress()).isEqualTo("192.168.0.1");
        message.release();
    }

    @Test
    void shouldCloseConnectionWhenV2VersionByteIsInvalid() {
        EmbeddedChannel channel = new EmbeddedChannel(new BoundedHAProxyMessageDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(V2_SIGNATURE, new byte[] {(byte) 0xFF}));

        assertThatThrownBy(() -> {
            for (int i = 0; i < 100; i++) {
                channel.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
            }
        }).isInstanceOf(HAProxyProtocolException.class);
        assertThat(channel.isOpen()).isFalse();
    }
}
