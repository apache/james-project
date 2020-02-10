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

package org.apache.james.imapserver.netty;


import static org.apache.james.imapserver.netty.ImapRequestFrameDecoder.NEEDED_DATA;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.imap.decode.ImapDecoder;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class ImapRequestFrameDecoderTest {
    ImapRequestFrameDecoder testee;

    @BeforeEach
    void setUp() {
        testee = new ImapRequestFrameDecoder(
            mock(ImapDecoder.class),
            12,
            18);
    }

    @Test
    void newCumulationBufferShouldNotThrowWhenNoAttachments() {
        ChannelHandlerContext channelHandler = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        ChannelConfig channelConfig = mock(ChannelConfig.class);

        when(channelConfig.getBufferFactory()).thenReturn(mock(ChannelBufferFactory.class));
        when(channelHandler.getChannel()).thenReturn(channel);
        when(channel.getConfig()).thenReturn(channelConfig);

        when(channelHandler.getAttachment()).thenReturn(ImmutableMap.<String, Object>of());

        assertThatCode(() -> testee.newCumulationBuffer(channelHandler, 36))
            .doesNotThrowAnyException();
    }

    @Test
    void newCumulationBufferShouldNotThrowOnNegativeSize() {
        ChannelHandlerContext channelHandler = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        ChannelConfig channelConfig = mock(ChannelConfig.class);

        when(channelConfig.getBufferFactory()).thenReturn(mock(ChannelBufferFactory.class));
        when(channelHandler.getChannel()).thenReturn(channel);
        when(channel.getConfig()).thenReturn(channelConfig);

        when(channelHandler.getAttachment()).thenReturn(ImmutableMap.<String, Object>of(NEEDED_DATA, -1));

        assertThatCode(() -> testee.newCumulationBuffer(channelHandler, 36))
            .doesNotThrowAnyException();
    }
}