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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class ImapRequestFrameDecoderTest {
    private static class Collector extends ChannelInboundHandlerAdapter {
        private final List<ImapMessage> messages = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            messages.add((ImapMessage) msg);
        }
    }

    @BeforeEach
    void synchronousScheduler() {
        // So that a literal spooled to a file is decoded before the assertions
        Schedulers.setFactory(new Schedulers.Factory() {
            @Override
            public Scheduler newBoundedElastic(int threadCap, int queuedTaskCap, ThreadFactory threadFactory, int ttlSeconds) {
                return Schedulers.immediate();
            }
        });
    }

    @AfterEach
    void resetScheduler() {
        Schedulers.resetFactory();
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

    private static List<byte[]> byteByByte(byte[] stream) {
        List<byte[]> chunks = new ArrayList<>();
        for (byte b : stream) {
            chunks.add(new byte[] {b});
        }
        return chunks;
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
    void eachSynchronizingLiteralOfACommandShouldGetItsContinuationRequest() {
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        });
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 2304, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(), 16, 0, 2304));
        channel.pipeline().addLast("collector", collector);

        // RFC 9051 section 7.5: the client sends a synchronizing literal once invited to
        channel.writeInbound(Unpooled.wrappedBuffer("a LOGIN {3}\r\n".getBytes(StandardCharsets.US_ASCII)));
        channel.writeInbound(Unpooled.wrappedBuffer("bob {6}\r\n".getBytes(StandardCharsets.US_ASCII)));
        channel.writeInbound(Unpooled.wrappedBuffer("secret\r\n".getBytes(StandardCharsets.US_ASCII)));

        List<String> continuationRequests = new ArrayList<>();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            continuationRequests.add(((ByteBuf) outbound).toString(StandardCharsets.US_ASCII));
        }
        channel.finishAndReleaseAll();

        assertThat(continuationRequests).containsExactly("+ Ok\r\n", "+ Ok\r\n");
        assertThat(collector.messages).hasSize(1)
            .first().asString().contains("LoginRequest", "localPart=bob");
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

    @Test
    void decodedCommandsShouldNotDependOnSegmentation() {
        byte[] stream = "a LOGIN {3}\r\nOGIN {bo\r\n".getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(false, 16, 0, 2304, List.of(stream));
        List<String> chunked = decode(false, 16, 0, 2304, List.of(
            Arrays.copyOfRange(stream, 0, 14), Arrays.copyOfRange(stream, 14, 18), Arrays.copyOfRange(stream, 18, 23)));

        assertThat(chunked).isEqualTo(whole);
    }

    @Test
    void decodedCommandsShouldNotDependOnSegmentationWhenAnAppendLiteralIsRejected() {
        byte[] stream = "a  OO,\r\nb APPEND INBOX {/L{\n}\rO4T0U=A\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(decode(true, 32, 5504, 3008, byteByByte(stream)))
            .isEqualTo(decode(true, 32, 5504, 3008, List.of(stream)));
    }

    @Test
    void decodedCommandsShouldNotDependOnSegmentationWhenALiteralSizeIsInvalid() {
        byte[] stream = "a NOOP\r\nb APPEND INBOX {\0\0\0\0\0\0\0\0\r\n\u0004\t@\0 }\r\n~b SELEa N\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(decode(true, 32, 5504, 3008, byteByByte(stream)))
            .isEqualTo(decode(true, 32, 5504, 3008, List.of(stream)));
    }

    @Test
    void restOfARejectedLiteralLineShouldNotBeDecodedAsACommand() {
        List<byte[]> chunks = List.of(
            "b APPEND INBOX {5}\r\n".getBytes(StandardCharsets.US_ASCII),
            "helloX".getBytes(StandardCharsets.US_ASCII),
            " c NOOP\r\n".getBytes(StandardCharsets.US_ASCII),
            "d NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=d, command=NOOP");
    }

    @Test
    void aCommandPipelinedAfterAFileBackedLiteralShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX {40}\r\n".getBytes(StandardCharsets.US_ASCII),
            "0123456789012345678901234567890123456789\r\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        // inMemorySizeLimit 32 < 40: the literal is spooled to a file
        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
    }

    @Test
    void aCommandPipelinedAfterAFileBackedLiteralEndingWithABareLfShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX {40}\r\n".getBytes(StandardCharsets.US_ASCII),
            "0123456789012345678901234567890123456789\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        // inMemorySizeLimit 32 < 40: the literal is spooled to a file
        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
    }

    @Test
    void aFileBackedLiteralEndingWithALfShouldStillExpectItsEndOfLine() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX {40}\r\n".getBytes(StandardCharsets.US_ASCII),
            "012345678901234567890123456789012345678\n\r\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
    }

    @Test
    void anUtf8AppendShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX UTF8 (~{5+}\r\nhello)\r\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        // inMemorySizeLimit 4096 > 5: the literal is read from memory
        assertThat(decode(true, 4096, 5504, 3008, chunks))
            .hasSize(2)
            .satisfies(messages -> assertThat(messages.get(0)).startsWith("AppendRequest{mailboxName=INBOX"))
            .satisfies(messages -> assertThat(messages.get(1)).contains("tag=b, command=NOOP"));
    }

    @Test
    void anUtf8AppendWithAFileBackedLiteralShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX UTF8 (~{40}\r\n".getBytes(StandardCharsets.US_ASCII),
            ("x".repeat(40) + ")\r\nb NOOP\r\n").getBytes(StandardCharsets.US_ASCII));

        // inMemorySizeLimit 32 < 40: the literal is spooled to a file
        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2)
            .satisfies(messages -> assertThat(messages.get(0)).startsWith("AppendRequest{mailboxName=INBOX"))
            .satisfies(messages -> assertThat(messages.get(1)).contains("tag=b, command=NOOP"));
    }

    @Test
    void aLiteralEndingWithABareLfAtTheEndOfTheDataShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX {5}\r\n".getBytes(StandardCharsets.US_ASCII),
            "hello\n".getBytes(StandardCharsets.US_ASCII));

        // inMemorySizeLimit 4096 > 5: the literal is read from memory
        assertThat(decode(true, 4096, 5504, 3008, chunks))
            .hasSize(1)
            .first().asString().startsWith("AppendRequest{mailboxName=INBOX");
    }

    @Test
    void aLiteralEndingWithABareLfAfterAFileBackedOneInTheSameSegmentShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            ("a APPEND INBOX {40}\r\n" + "x".repeat(40) + "\r\n"
                + "b APPEND INBOX {40}\r\n" + "x".repeat(40) + "\n").getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 32, 5504, 3008, chunks))
            .hasSize(2);
    }

    @Test
    void aLiteralFollowedByACrOnlyShouldWaitForItsLf() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX {5}\r\n".getBytes(StandardCharsets.US_ASCII),
            "hello\r".getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 4096, 5504, 3008, chunks))
            .isEmpty();
    }

    @Test
    void restOfALineRejectedForItsLengthShouldNotDependOnSegmentation() {
        byte[] stream = ("a LOGIN {3}\r\nbob " + "x".repeat(300) + "\r\nc NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(false, 32, 0, 256, List.of(stream));
        List<String> chunked = decode(false, 32, 0, 256, byteByByte(stream));

        assertThat(whole).isEqualTo(chunked);
        assertThat(whole).last().asString().contains("tag=c, command=NOOP");
    }

    @Test
    void restOfAnUnterminatedQuotedStringRejectedForItsLengthShouldNotDependOnSegmentation() {
        byte[] stream = ("a APPEND INBOX \"\r\n" + ("x".repeat(50) + "\r\n").repeat(5) + "c NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 32, 1024, 256, List.of(stream));
        List<String> chunked = decode(true, 32, 1024, 256, byteByByte(stream));

        assertThat(whole).isEqualTo(chunked);
        assertThat(whole).last().asString().contains("tag=c, command=NOOP");
    }

    @Test
    void aNonSynchronizingLiteralOfARejectedCommandShouldNotBeDecodedAsCommands() {
        byte[] stream = ("a APPEND INBOX \"99-Foo-2026 10:00:00 +0000\" {24+}\r\n"
            + "x1 NOOP\r\n"
            + "x2 CREATE pwn\r\n"
            + "\r\n"
            + "b NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
        assertThat(decode(true, 1024, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void aNonSynchronizingLiteralWhoseBraceWasReadByTheRejectedCommandShouldNotBeDecodedAsCommands() {
        byte[] stream = ("a ID (\"name\" {24+}\r\n"
            + "x1 NOOP\r\n"
            + "x2 CREATE pwn\r\n"
            + ")\r\n"
            + "b NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
        assertThat(decode(true, 1024, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void aNonSynchronizingLiteralSizeWithManyLeadingZerosOfARejectedCommandShouldNotBeDecodedAsCommands() {
        byte[] stream = ("a APPEND INBOX \"99-Foo-2026 10:00:00 +0000\" {" + "0".repeat(64) + "24+}\r\n"
            + "x1 NOOP\r\n"
            + "x2 CREATE pwn\r\n"
            + "\r\n"
            + "b NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
        assertThat(decode(true, 1024, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void aBinaryLiteralOfARejectedSetMetadataShouldNotBeDecodedAsCommands() {
        byte[] stream = ("a SETMETADATA INBOX (/private/comment ~{24+}\r\n"
            + "x1 NOOP\r\n"
            + "x2 CREATE pwn\r\n"
            + ")\r\n"
            + "b NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
        assertThat(decode(true, 1024, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void aBinaryLiteralOfARejectedCommandShouldNotBeDecodedAsCommands() {
        List<byte[]> chunks = List.of(
            "a APPEND INBOX (\\Seen) \"14-Nov-2026 10:00:00 +0000\" ~{24+}\r\n".getBytes(StandardCharsets.US_ASCII),
            "x1 NOOP\r\nx2 CREATE pwn\r\n\r\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(decode(true, 1024, 5504, 3008, chunks))
            .hasSize(2)
            .last().asString().contains("tag=b, command=NOOP");
    }

    @Test
    void aNonSynchronizingLiteralTooBigForTheServerShouldBeRejectedAndCloseTheConnection() {
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        // literalSizeLimit 8 < 9
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(),
            1024, 8, 3008));
        channel.pipeline().addLast("collector", collector);

        channel.writeInbound(Unpooled.wrappedBuffer(
            "a APPEND INBOX {9+}\r\nx1 NOOP\r\n\r\nb NOOP\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertThat(collector.messages).noneMatch(message -> message.toString().contains("command=NOOP"));
        ByteBuf answer = channel.readOutbound();
        assertThat(answer.toString(StandardCharsets.US_ASCII)).startsWith("a BAD APPEND");
        answer.release();
        assertThat(channel.isOpen()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void aSynchronizingLiteralTooBigForTheServerShouldBeRejectedBeforeTheContinuationRequest() {
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(),
            1024, 5504, 3008));

        channel.writeInbound(Unpooled.wrappedBuffer("a APPEND INBOX {999999999999}\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertThat((Object) channel.readOutbound()).isNull();
        assertThat(channel.readInbound().toString()).contains("BAD");
        channel.finishAndReleaseAll();
    }

    @Test
    void aMalformedLiteralAnnouncementShouldNotSwallowTheNextCommands() {
        byte[] stream = "a SELECT {1\r\nb NOOP\r\nc NOOP}\r\nd NOOP\r\n".getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(whole).hasSize(4);
        assertThat(whole.get(0)).contains("serverResponseType=BAD, tag=a");
        assertThat(whole.get(1)).contains("tag=b, command=NOOP");
        assertThat(whole.get(2)).contains("serverResponseType=BAD, tag=c");
        assertThat(whole.get(3)).contains("tag=d, command=NOOP");
        assertThat(decode(true, 1024, 5504, 3008, byteByByte(stream))).isEqualTo(whole);
    }

    @Test
    void aFileBackedLiteralFollowedByMoreArgumentsShouldBeDecodedAsInMemory() {
        byte[] stream = ("b SETMETADATA INBOX (/private/comment {34}\r\n" + "y".repeat(34)
            + " /private/other \"v\")\r\n"
            + "c NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> inMemory = decode(true, 1024, 5504, 3008, List.of(stream));
        assertThat(inMemory).first().asString().contains("SetMetadataRequest");
        assertThat(decode(true, 16, 5504, 3008, List.of(stream)))
            .isEqualTo(inMemory);
        assertThat(decode(true, 16, 5504, 3008, byteByByte(stream)))
            .isEqualTo(inMemory);
    }

    @Test
    void aNonSynchronizingLiteralAfterAFileBackedLiteralShouldNotBeDecodedAsCommands() {
        byte[] stream = ("b SETMETADATA INBOX (/private/comment {34}\r\n" + "y".repeat(34)
            + " /private/other {13+}\r\n"
            + "x1 CREATE pwn)\r\n"
            + "c NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 16, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=c, command=NOOP");
        assertThat(decode(true, 16, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @ParameterizedTest
    @ValueSource(strings = {"~{13+}", "{0+}"})
    void otherNonSynchronizingLiteralsAfterAFileBackedLiteralShouldNotBeDecodedAsCommands(String announcement) {
        String literal = announcement.startsWith("~") ? "x1 CREATE pwn" : "";
        byte[] stream = ("b SETMETADATA INBOX (/private/comment {34}\r\n" + "y".repeat(34)
            + " /private/other " + announcement + "\r\n"
            + literal + ")\r\n"
            + "c NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 16, 5504, 3008, List.of(stream));
        assertThat(whole)
            .hasSize(2)
            .last().asString().contains("tag=c, command=NOOP");
        assertThat(decode(true, 16, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void aSecondLiteralOnTheLineOfAFileBackedLiteralShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            "c SETMETADATA INBOX (/private/comment {34}\r\n".getBytes(StandardCharsets.US_ASCII),
            ("y".repeat(34) + " /private/other {3+}\r\nabc)\r\n").getBytes(StandardCharsets.US_ASCII));
        assertThat(decode(true, 32, 0, 3008, chunks)).isEqualTo(decode(true, 4096, 0, 3008, chunks));
        assertThat(decode(true, 32, 0, 3008, chunks)).last().asString().contains("SetMetadataRequest").contains("/private/other");
    }

    @Test
    void aSynchronizingSecondLiteralOnTheLineOfAFileBackedLiteralShouldGetItsContinuationRequest() {
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(), 32, 0, 3008));
        channel.pipeline().addLast("collector", collector);

        channel.writeInbound(Unpooled.wrappedBuffer("c SETMETADATA INBOX (/private/comment {34}\r\n".getBytes(StandardCharsets.US_ASCII)));
        channel.writeInbound(Unpooled.wrappedBuffer(("y".repeat(34) + " /private/other {3}\r\n").getBytes(StandardCharsets.US_ASCII)));
        List<String> continuationRequests = new ArrayList<>();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            continuationRequests.add(((ByteBuf) outbound).toString(StandardCharsets.US_ASCII));
        }
        // The client sends the synchronizing literal once invited to
        channel.writeInbound(Unpooled.wrappedBuffer("abc)\r\n".getBytes(StandardCharsets.US_ASCII)));
        channel.finishAndReleaseAll();

        assertThat(continuationRequests).containsExactly("+ Ok\r\n", "+ Ok\r\n");
        assertThat(collector.messages).hasSize(1)
            .first().asString().contains("SetMetadataRequest", "/private/other", "abc");
    }

    @Test
    void aLiteralAnnouncedAcrossTheLengthLimitOfAFileBackedLiteralLineShouldNotBeDecodedAsCommands() {
        // The first 3008 bytes after the literal end with "{6": the announcement goes on past the length limit
        byte[] stream = ("b SETMETADATA INBOX (/private/c {34}\r\n" + "y".repeat(34)
            + " ".repeat(3006) + "{6+}\r\n"
            + "x NOOP\r\n"
            + "c NOOP\r\n").getBytes(StandardCharsets.US_ASCII);

        List<String> whole = decode(true, 16, 5504, 3008, List.of(stream));
        assertThat(whole).noneMatch(message -> message.contains("tag=x"));
        assertThat(whole).last().asString().contains("tag=c, command=NOOP");
        assertThat(decode(true, 16, 5504, 3008, byteByByte(stream)))
            .isEqualTo(whole);
    }

    @Test
    void neededDataShouldCountTheEarlierLiterals() {
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        Map<String, Object> attachment = new ConcurrentHashMap<>();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(attachment);
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(), 50, 0, 3008));

        // A literal of 5 bytes, then a literal of 100 bytes, above the in memory size limit, not received yet
        channel.writeInbound(Unpooled.wrappedBuffer("a APPEND {5+}\r\nINBOX {100+}\r\n".getBytes(StandardCharsets.US_ASCII)));

        // The 100 bytes of the literal, then the CRLF ending the command
        assertThat(attachment.get(ImapRequestFrameDecoder.NEEDED_DATA)).isEqualTo(100 + 2);
        channel.finishAndReleaseAll();
    }

    @Test
    void neededDataShouldNotOverflowForTheLargestLiteral() {
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        Map<String, Object> attachment = new ConcurrentHashMap<>();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(attachment);
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        // No literal size limit
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(), 50, 0, 3008));

        channel.writeInbound(Unpooled.wrappedBuffer("a APPEND INBOX {2147483647+}\r\n".getBytes(StandardCharsets.US_ASCII)));

        // The literal and the CRLF ending the command exceed what can be counted: the command is rejected
        assertThat(attachment.get(ImapRequestFrameDecoder.NEEDED_DATA)).isNull();
        channel.finishAndReleaseAll();
    }

    @Test
    void theLargestSynchronizingLiteralShouldNotGetAContinuationRequest() {
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        // No literal size limit
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(), 50, 0, 3008));

        channel.writeInbound(Unpooled.wrappedBuffer("a APPEND INBOX {2147483647}\r\n".getBytes(StandardCharsets.US_ASCII)));

        // The literal is refused: the client is not invited to send it
        Object inbound = channel.readInbound();
        assertThat(String.valueOf(inbound)).contains("serverResponseType=BAD");
        assertThat((Object) channel.readOutbound()).isNull();
        channel.finishAndReleaseAll();
    }

    /**
     * As {@link #decode(boolean, int, int, int, List)}, also recording the exceptions raised, in order, as the session
     * handler would get them.
     */
    private static List<String> decodeWithErrors(int inMemorySizeLimit, int maxFrameLength, List<byte[]> chunks) {
        return decodeWithErrors(inMemorySizeLimit, 0, maxFrameLength, chunks);
    }

    private static List<String> decodeWithErrors(int inMemorySizeLimit, int literalSizeLimit, int maxFrameLength, List<byte[]> chunks) {
        List<String> events = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), maxFrameLength, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(),
            inMemorySizeLimit, literalSizeLimit, maxFrameLength));
        channel.pipeline().addLast("collector", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                events.add(msg.toString());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                events.add(cause.getClass().getName());
            }
        });

        chunks.forEach(chunk -> channel.writeInbound(Unpooled.wrappedBuffer(chunk)));
        channel.finishAndReleaseAll();
        return events;
    }

    @Test
    void aTooLongLineAfterALiteralShouldNotDependOnSegmentation() {
        String literalCommand = "a SETMETADATA INBOX (/private/comment {20}\r\n" + "x".repeat(20) + "\n";
        String rest = "b SELECT " + "x".repeat(300) + "\r\nc NOOP\r\n";
        byte[] stream = (literalCommand + rest).getBytes(StandardCharsets.US_ASCII);
        List<String> whole = decodeWithErrors(32, 256, List.of(stream));
        List<String> chunked = decodeWithErrors(32, 256, List.of(literalCommand.getBytes(StandardCharsets.US_ASCII), rest.getBytes(StandardCharsets.US_ASCII)));
        assertThat(whole).isEqualTo(chunked);
        assertThat(whole).hasSize(3);
        assertThat(whole.get(1)).isEqualTo(TooLongFrameException.class.getName());
        assertThat(whole.get(2)).contains("tag=c, command=NOOP");
    }

    @Test
    void aLiteralAnnouncedAtTheEndOfATooLongLineShouldNotBeDecodedAsCommands() {
        String literalCommand = "a SETMETADATA INBOX (/private/comment {20}\r\n" + "x".repeat(20) + "\n";
        String rest = "b SELECT " + "x".repeat(300) + " {14+}\r\nc DELETE INBOX\r\nd NOOP\r\n";
        byte[] stream = (literalCommand + rest).getBytes(StandardCharsets.US_ASCII);
        byte[] restBytes = rest.getBytes(StandardCharsets.US_ASCII);
        List<byte[]> byteByByte = new ArrayList<>();
        byteByByte.add(literalCommand.getBytes(StandardCharsets.US_ASCII));
        byteByByte.addAll(byteByByte(restBytes));

        List<String> whole = decodeWithErrors(32, 256, List.of(stream));
        assertThat(whole).hasSize(3);
        assertThat(whole.get(0)).contains("tag=a");
        assertThat(whole.get(1)).isEqualTo(TooLongFrameException.class.getName());
        assertThat(whole.get(2)).contains("tag=d, command=NOOP");
        assertThat(decodeWithErrors(32, 256, List.of(literalCommand.getBytes(StandardCharsets.US_ASCII), restBytes)))
            .isEqualTo(whole);
        assertThat(decodeWithErrors(32, 256, byteByByte))
            .isEqualTo(whole);
    }

    @Test
    void theRestOfALiteralWhoseSpoolingFailedShouldNotBeDecodedAsCommands() {
        List<String> events = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel();
        FakeImapSession session = new FakeImapSession() {
            @Override
            public boolean needsCommandInjectionDetection() {
                return false;
            }
        };
        session.authenticated();
        channel.attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).set(session);
        channel.attr(NettyConstants.FRAME_DECODE_ATTACHMENT_ATTRIBUTE_KEY).set(new ConcurrentHashMap<>());
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 3008, false));
        channel.pipeline().addLast("decoder", new ImapRequestFrameDecoder(new DefaultImapDecoderFactory().buildImapDecoder(),
            16, 0, 3008, () -> new ImapRequestFrameDecoder.FileChunkConsumer() {
                @Override
                public void accept(byte[] next) {
                    throw new RuntimeException("No space left on device");
                }
            }));
        channel.pipeline().addLast("collector", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                events.add(msg.toString());
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                events.add(cause.getClass().getName());
            }
        });

        String literal = "0123456789\r\nx1 CREATE pwn\r\nx2 NOOP\r\n";
        channel.writeInbound(Unpooled.wrappedBuffer(("a APPEND INBOX {" + literal.length() + "+}\r\n" + literal.substring(0, 5))
            .getBytes(StandardCharsets.US_ASCII)));
        channel.writeInbound(Unpooled.wrappedBuffer((literal.substring(5) + "\r\n").getBytes(StandardCharsets.US_ASCII)));
        channel.finishAndReleaseAll();

        assertThat(events).containsExactly(RuntimeException.class.getName());
    }

    @Test
    void commandsAfterATooLongLineReceivedInSeveralSegmentsShouldBeDecoded() {
        List<byte[]> chunks = List.of(
            ("a " + "x".repeat(200)).getBytes(StandardCharsets.US_ASCII),
            "x".repeat(100).getBytes(StandardCharsets.US_ASCII),
            "\r\nb NOOP\r\nc NOOP\r\n".getBytes(StandardCharsets.US_ASCII));

        List<String> chunked = decodeWithErrors(4096, 256, chunks);
        assertThat(chunked).hasSize(3);
        assertThat(chunked.get(0)).isEqualTo(TooLongFrameException.class.getName());
        assertThat(chunked.get(1)).contains("tag=b, command=NOOP");
        assertThat(chunked.get(2)).contains("tag=c, command=NOOP");
        assertThat(decodeWithErrors(4096, 256, List.of(String.join("", "a ", "x".repeat(300), "\r\nb NOOP\r\nc NOOP\r\n").getBytes(StandardCharsets.US_ASCII))))
            .isEqualTo(chunked);
    }

    @Test
    void literalContentOfARejectedAppendShouldNotBeTakenForALiteralAnnouncement() {
        byte[] stream = "a APPEND INBOX {3+}\r\n{9+}\r\nb NOOP\r\nc NOOP\r\n".getBytes(StandardCharsets.US_ASCII);

        assertThat(decode(true, 4096, 0, 8192, List.of(stream)))
            .anySatisfy(message -> assertThat(message).contains("tag=b, command=NOOP"))
            .anySatisfy(message -> assertThat(message).contains("tag=c, command=NOOP"));
    }

    @Test
    void aLineOfExactlyMaxFrameLengthShouldNotDependOnWhereItsCrlfIsSplit() {
        int maxFrameLength = 256;
        // 10 + 245 + 1 = 256 octets before the CRLF
        String line = "a SELECT \"" + "x".repeat(245) + "\"";

        List<String> whole = decodeWithErrors(4096, maxFrameLength,
            List.of((line + "\r\n").getBytes(StandardCharsets.US_ASCII)));
        List<String> crThenLf = decodeWithErrors(4096, maxFrameLength, List.of(
            (line + "\r").getBytes(StandardCharsets.US_ASCII),
            "\n".getBytes(StandardCharsets.US_ASCII)));

        assertThat(whole).hasSize(1);
        assertThat(whole.get(0)).doesNotContain(TooLongFrameException.class.getName());
        assertThat(crThenLf).isEqualTo(whole);
    }

    @Test
    void aLineOfMaxFrameLengthPlusOneShouldBeRejectedWhenItsCrArrivesAlone() {
        String line = "a SELECT \"" + "x".repeat(246) + "\"";

        assertThat(decodeWithErrors(4096, 256, List.of(
                (line + "\r").getBytes(StandardCharsets.US_ASCII),
                "\n".getBytes(StandardCharsets.US_ASCII))))
            .containsExactly(TooLongFrameException.class.getName());
    }

    @Test
    void aRejectedCommandEndingWithATooBigNonSynchronizingLiteralShouldBeAnsweredWhateverTheSegmentation() {
        String head = "a APPEND INBOX {3+}\r\nabc x ";
        String tail = "{600+}\r\nb NOOP\r\n";
        List<String> whole = decodeWithErrors(4096, 384, 8192, List.of((head + tail).getBytes(StandardCharsets.US_ASCII)));
        List<String> chunked = decodeWithErrors(4096, 384, 8192,
            List.of(head.getBytes(StandardCharsets.US_ASCII), tail.getBytes(StandardCharsets.US_ASCII)));

        assertThat(chunked.get(0)).contains("BAD").contains("tag=a");
        assertThat(whole).isEqualTo(chunked);
    }

    @Test
    void aTooLongLineRejectedBeforeItsEndAfterALiteralShouldNotDependOnSegmentation() {
        String head = "a FOO {3+}\r\nabc\r\nc FOO ";
        String tail = "x".repeat(300) + "\r\nb NOOP\r\n";
        List<String> whole = decodeWithErrors(32, 256, List.of((head + tail).getBytes(StandardCharsets.US_ASCII)));
        List<String> chunked = decodeWithErrors(32, 256,
            List.of(head.getBytes(StandardCharsets.US_ASCII), tail.getBytes(StandardCharsets.US_ASCII)));

        assertThat(whole).contains(TooLongFrameException.class.getName());
        assertThat(chunked).isEqualTo(whole);
    }
}
