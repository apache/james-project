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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import org.apache.james.protocols.netty.CommandInjectionDetectedException;
import org.junit.jupiter.api.Test;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.AttributeKey;

class SwitchableLineBasedFrameDecoderTest {
    private static final AttributeKey<Object> START_TLS_IN_FLIGHT = AttributeKey.valueOf("startTlsInFlight");
    private static final List<String> TOKENS = List.of("starttls", "STARTTLS", "a", "a1", "x", " ", "  ", "\t",
        "\r", "\n", "\r\n", "\r\n", "\r\n", "a starttls", "a1 STARTTLS\r\n", " starttls", "starttls x", "noop", "é");

    /**
     * The STARTTLS checks as they were, on the whole read buffer, for each line.
     */
    private static class WholeBufferCheck extends LineBasedFrameDecoder {
        private static final Splitter CRLF_SPLITTER = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings();
        private static final String PATTERN = "starttls";

        WholeBufferCheck() {
            super(1024, false, false);
        }

        private static String removeTag(String input) {
            String trimmedInput = input.trim();
            int tagEnd = trimmedInput.indexOf(' ');
            if (tagEnd < 0) {
                return input;
            }
            return trimmedInput.substring(tagEnd + 1);
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
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
            return super.decode(ctx, buffer);
        }
    }

    record Outcome(List<String> frames, Optional<String> error, Object startTlsInFlight) {
    }

    private static EmbeddedChannel channel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(NettyConstants.FRAMER, new SwitchableLineBasedFrameDecoder(channel.pipeline(), 1024, false));
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
            EmbeddedChannel expected = new EmbeddedChannel(new WholeBufferCheck());
            EmbeddedChannel actual = channel();

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
                    .describedAs("segments %s", segments)
                    .isEqualTo(expectedOutcome);
                if (expectedOutcome.error().isPresent()) {
                    break;
                }
            }
            close(expected);
            close(actual);
        }
    }

    @Test
    void manyCommandsInASingleSegmentShouldBeFramedInLinearTime() {
        EmbeddedChannel channel = channel();
        int count = 100_000;

        long start = System.nanoTime();
        channel.writeInbound(Unpooled.copiedBuffer("a1 NOOP\r\n".repeat(count), StandardCharsets.US_ASCII));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Scanning the whole read buffer for each line takes minutes
        assertThat(channel.inboundMessages()).hasSize(count);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
        channel.finishAndReleaseAll();
    }

    @Test
    void startTlsFollowedByCommandsInTheSameSegmentShouldBeRejected() {
        EmbeddedChannel channel = channel();

        assertThatThrownBy(() -> channel.writeInbound(Unpooled.copiedBuffer("a1 NOOP\r\n".repeat(1000) + "a2 STARTTLS\r\na3 LOGIN user password\r\n", StandardCharsets.US_ASCII)))
            .matches(e -> e instanceof CommandInjectionDetectedException || e.getCause() instanceof CommandInjectionDetectedException);
        assertThat(channel.inboundMessages()).isEmpty();
    }
}
