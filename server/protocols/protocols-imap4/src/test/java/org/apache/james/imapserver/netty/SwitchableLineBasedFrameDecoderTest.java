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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.protocols.netty.CommandInjectionDetectedException;
import org.junit.jupiter.api.Test;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ByteProcessor;

class SwitchableLineBasedFrameDecoderTest {
    private static final AttributeKey<Object> START_TLS_IN_FLIGHT = AttributeKey.valueOf("startTlsInFlight");
    private static final List<String> TOKENS = List.of("starttls", "STARTTLS", "a", "a1", "x", " ", "  ", "\t",
        "\r", "\n", "\r\n", "\r\n", "\r\n", "a starttls", "a1 STARTTLS\r\n", " starttls", "starttls x", "noop", "a1 longer line ", "é");

    /**
     * The STARTTLS checks as they were, on the whole read buffer, for each line, followed by the framing of the IMAP
     * framer, which hands a too long line over.
     */
    private static class WholeBufferCheck extends ByteToMessageDecoder {
        private static final Splitter CRLF_SPLITTER = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings();
        private static final String PATTERN = "starttls";
        private final int maxFrameLength;

        WholeBufferCheck(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            Object decoded = decode(ctx, in);
            if (decoded != null) {
                out.add(decoded);
            }
        }

        private Object decodeLine(ByteBuf buffer) {
            int lineEnd = buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), (byte) '\n');
            int endOfLine = lineEnd < 0 ? -1 : lineEnd - buffer.readerIndex();
            int lineLength;
            if (endOfLine < 0) {
                lineLength = ImapRequestFrameDecoder.lengthWithoutATrailingCr(buffer);
            } else if (endOfLine > 0 && buffer.getByte(buffer.readerIndex() + endOfLine - 1) == '\r') {
                lineLength = endOfLine - 1;
            } else {
                lineLength = endOfLine;
            }
            if (lineLength > maxFrameLength) {
                return buffer.readRetainedSlice(endOfLine < 0 ? buffer.readableBytes() : endOfLine + 1);
            }
            if (endOfLine < 0) {
                return null;
            }
            return buffer.readRetainedSlice(endOfLine + 1);
        }

        private static String removeTag(String input) {
            String trimmedInput = input.trim();
            int tagEnd = trimmedInput.indexOf(' ');
            if (tagEnd < 0) {
                return input;
            }
            return trimmedInput.substring(tagEnd + 1);
        }

        private Object decode(ChannelHandlerContext ctx, ByteBuf buffer) {
            boolean startTlsInFlight = Optional.ofNullable(ctx.channel().attr(START_TLS_IN_FLIGHT).get())
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
            if (startTlsInFlight) {
                throw new CommandInjectionDetectedException(buffer.toString(StandardCharsets.US_ASCII).trim().toLowerCase(Locale.US));
            }
            if (buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), (byte) '\n') >= 0) {
                String input = buffer.toString(StandardCharsets.US_ASCII).trim().toLowerCase(Locale.US);
                List<String> parts = CRLF_SPLITTER.splitToList(input);
                boolean injection = parts.stream().map(WholeBufferCheck::removeTag).anyMatch(line -> line.startsWith(PATTERN) && !line.endsWith(PATTERN))
                    || parts.stream().map(WholeBufferCheck::removeTag).anyMatch(line -> line.startsWith(PATTERN)) && parts.size() > 1;
                if (injection) {
                    throw new CommandInjectionDetectedException(input);
                }
                if (CRLF_SPLITTER.splitToStream(removeTag(input)).anyMatch(s -> s.equalsIgnoreCase(PATTERN))) {
                    ctx.channel().attr(START_TLS_IN_FLIGHT).set(true);
                }
            }
            return decodeLine(buffer);
        }
    }

    record Outcome(List<String> frames, Optional<String> error, Object startTlsInFlight) {
    }

    private static EmbeddedChannel channel(int maxFrameLength) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), maxFrameLength, false));
        return channel;
    }

    private static Outcome write(EmbeddedChannel channel, String segment) {
        Optional<String> error = Optional.empty();
        try {
            channel.writeInbound(Unpooled.copiedBuffer(segment, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            error = Optional.of(String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
        List<String> frames = new ArrayList<>();
        ByteBuf frame;
        while ((frame = channel.readInbound()) != null) {
            frames.add(frame.toString(StandardCharsets.ISO_8859_1));
            frame.release();
        }
        return new Outcome(frames, error, channel.attr(START_TLS_IN_FLIGHT).get());
    }

    private static void close(EmbeddedChannel channel) {
        try {
            channel.finishAndReleaseAll();
        } catch (Exception e) {
            // Decoding what is left on close fails as decoding it before did
        }
    }

    @Test
    void startTlsChecksShouldGiveTheAnswersOfTheWholeBufferCheck() {
        Random random = new Random(42);
        for (int trial = 0; trial < 20_000; trial++) {
            // Short lines exercise the hand over of too long lines
            int maxFrameLength = random.nextBoolean() ? 1024 : 1 + random.nextInt(12);
            EmbeddedChannel expected = new EmbeddedChannel(new WholeBufferCheck(maxFrameLength));
            EmbeddedChannel actual = channel(maxFrameLength);

            StringBuilder input = new StringBuilder();
            int tokens = random.nextInt(12);
            for (int i = 0; i < tokens; i++) {
                input.append(TOKENS.get(random.nextInt(TOKENS.size())));
            }
            List<String> segments = new ArrayList<>();
            int position = 0;
            while (position < input.length()) {
                int length = 1 + random.nextInt(input.length() - position);
                segments.add(input.substring(position, position + length));
                position += length;
            }

            for (String segment : segments) {
                Outcome expectedOutcome = write(expected, segment);
                Outcome actualOutcome = write(actual, segment);
                assertThat(actualOutcome)
                    .describedAs("segments %s, max frame length %d", segments, maxFrameLength)
                    .isEqualTo(expectedOutcome);
                if (expectedOutcome.error().filter(error -> error.startsWith("Client input")).isPresent()) {
                    break;
                }
            }
            close(expected);
            close(actual);
        }
    }

    @Test
    void manyCommandsInASingleSegmentShouldBeReadABoundedNumberOfTimes() {
        EmbeddedChannel channel = channel(1024);
        int count = 2_000;
        byte[] bytes = ("a1 NOOP\r\n".repeat(count) + "a2 APPEND").getBytes(StandardCharsets.US_ASCII);
        // Not recording the invocations: a regression would read the buffer a lot. Written as a single segment, it
        // becomes the read buffer of the framer
        ByteBuf buffer = mock(UnpooledHeapByteBuf.class, withSettings()
            .spiedInstance(new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, bytes.length, bytes.length))
            .defaultAnswer(CALLS_REAL_METHODS)
            .stubOnly());
        AtomicLong readBytes = new AtomicLong();
        doAnswer(invocation -> {
            readBytes.addAndGet((int) invocation.getArgument(1));
            return invocation.callRealMethod();
        }).when(buffer).toString(anyInt(), anyInt(), any(Charset.class));
        // Searches stop on the byte found: counting the bytes visited
        doAnswer(invocation -> {
            int index = invocation.getArgument(0);
            int length = invocation.getArgument(1);
            int found = (int) invocation.callRealMethod();
            readBytes.addAndGet(found < 0 ? length : found - index + 1);
            return found;
        }).when(buffer).forEachByte(anyInt(), anyInt(), any(ByteProcessor.class));
        doAnswer(invocation -> {
            int index = invocation.getArgument(0);
            int length = invocation.getArgument(1);
            int found = (int) invocation.callRealMethod();
            readBytes.addAndGet(found < 0 ? length : index + length - found);
            return found;
        }).when(buffer).forEachByteDesc(anyInt(), anyInt(), any(ByteProcessor.class));
        doAnswer(invocation -> {
            int from = invocation.getArgument(0);
            int to = invocation.getArgument(1);
            int found = (int) invocation.callRealMethod();
            readBytes.addAndGet(found < 0 ? Math.abs(to - from) : Math.abs(found - from) + 1);
            return found;
        }).when(buffer).indexOf(anyInt(), anyInt(), anyByte());
        buffer.writeBytes(bytes);

        channel.writeInbound(buffer);

        // Reading the whole read buffer for each line reads it thousands of times
        assertThat(channel.inboundMessages()).hasSize(count);
        assertThat(readBytes.get()).isLessThanOrEqualTo(10L * bytes.length);
        channel.finishAndReleaseAll();
    }

    @Test
    void startTlsFollowedByCommandsInTheSameSegmentShouldBeRejected() {
        EmbeddedChannel channel = channel(1024);

        assertThatThrownBy(() -> channel.writeInbound(Unpooled.copiedBuffer("a1 NOOP\r\n".repeat(1000) + "a2 STARTTLS\r\na3 LOGIN user password\r\n", StandardCharsets.US_ASCII)))
            .matches(e -> e instanceof CommandInjectionDetectedException || e.getCause() instanceof CommandInjectionDetectedException);
        assertThat(channel.inboundMessages()).isEmpty();
    }
}
