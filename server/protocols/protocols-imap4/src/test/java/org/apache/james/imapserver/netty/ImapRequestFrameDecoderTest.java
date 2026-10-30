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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

class ImapRequestFrameDecoderTest {
    private static class Collector extends ChannelInboundHandlerAdapter {
        private final List<ImapMessage> messages = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            messages.add((ImapMessage) msg);
        }
    }

    private static List<String> decode(boolean authenticated, int inMemorySizeLimit, int literalSizeLimit, int maxFrameLength, List<byte[]> chunks) {
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        if (authenticated) {
            session.authenticated();
        }
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), maxFrameLength, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(),
            inMemorySizeLimit, literalSizeLimit, maxFrameLength));
        channel.pipeline().addLast("collector", collector);

        chunks.forEach(chunk -> channel.writeInbound(Unpooled.wrappedBuffer(chunk)));
        channel.finishAndReleaseAll();

        return collector.messages.stream()
            .map(Object::toString)
            .toList();
    }

    @Test
    void decodeShouldNotFailWhenARetriedLiteralCommandEndsBeforeTheLastChunk() {
        List<byte[]> chunks = List.of(
            "b APPEND INBOX {5}\r\n".getBytes(StandardCharsets.US_ASCII),
            "hello\n".getBytes(StandardCharsets.US_ASCII),
            "c NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=c, command=NOOP");
    }

    @Test
    void decodeShouldNotFailWhenARetriedLiteralCommandIsRejectedBeforeTheLastChunk() {
        List<byte[]> chunks = List.of(
            "b APPEND INBOX {5}\r\n".getBytes(StandardCharsets.US_ASCII),
            "helloX".getBytes(StandardCharsets.US_ASCII),
            "\r\nc NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThatCode(() -> decode(true, 32, 5504, 3008, chunks))
            .doesNotThrowAnyException();
    }
}
