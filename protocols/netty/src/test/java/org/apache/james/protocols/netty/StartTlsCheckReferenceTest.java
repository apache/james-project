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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.AttributeKey;

/**
 * The STARTTLS checks once scanned the whole read buffer for each line framed. They now split it once: their
 * answers shall not change.
 */
class StartTlsCheckReferenceTest {
    private static final AttributeKey<Object> START_TLS_IN_FLIGHT = AttributeKey.valueOf("startTlsInFlight");
    private static final List<String> TOKENS = List.of("starttls", "STARTTLS", "stls", "noop", "a", "x", " ", "  ",
        "\t", "\r", "\n", "\r\n", "\r\n", "\r\n", "starttls\r\n", "a starttls", "starttls x", "é");

    /**
     * The STARTTLS checks as they were, on the whole read buffer, for each line.
     */
    private static class WholeBufferCheck extends LineBasedFrameDecoder {
        private static final Splitter CRLF_SPLITTER = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings();
        private final String pattern;

        WholeBufferCheck(String pattern) {
            super(1024, false, false);
            this.pattern = pattern;
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
                boolean injection = parts.stream().anyMatch(line -> line.startsWith(pattern) && !line.endsWith(pattern))
                    || parts.stream().anyMatch(line -> line.startsWith(pattern)) && parts.size() > 1;
                if (injection) {
                    throw new CommandInjectionDetectedException(input);
                }
                if (CRLF_SPLITTER.splitToStream(input).anyMatch(s -> s.equalsIgnoreCase(pattern))) {
                    ctx.channel().attr(START_TLS_IN_FLIGHT).set(true);
                }
            }
            return super.decode(ctx, buffer);
        }
    }

    record Outcome(List<String> frames, Optional<String> error, Object startTlsInFlight) {
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

    @ParameterizedTest
    @ValueSource(strings = {"starttls", "stls"})
    void answersShouldBeTheOnesOfTheWholeBufferCheck(String pattern) {
        Random random = new Random(42);
        for (int trial = 0; trial < 20_000; trial++) {
            EmbeddedChannel expected = new EmbeddedChannel(new WholeBufferCheck(pattern));
            EmbeddedChannel actual = new EmbeddedChannel();
            actual.pipeline().addLast(HandlerConstants.FRAMER, new AllButStartTlsLineBasedChannelHandler(actual.pipeline(), 1024, false, pattern));
            actual.pipeline().addLast(HandlerConstants.CORE_HANDLER, new ChannelInboundHandlerAdapter());

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

    private static void close(EmbeddedChannel channel) {
        try {
            channel.finishAndReleaseAll();
        } catch (Exception e) {
            // Decoding what is left on close fails as decoding it before did
        }
    }
}
