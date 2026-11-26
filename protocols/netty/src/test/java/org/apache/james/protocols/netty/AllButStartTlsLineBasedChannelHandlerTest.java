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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.protocols.api.CommandDetectionSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;

class AllButStartTlsLineBasedChannelHandlerTest {
    private static class DetectionSession implements CommandDetectionSession {
        private boolean detection = true;

        @Override
        public boolean needsCommandInjectionDetection() {
            return detection;
        }

        @Override
        public void startDetectingCommandInjection() {
            detection = true;
        }

        @Override
        public void stopDetectingCommandInjection() {
            detection = false;
        }
    }

    private EmbeddedChannel channel;
    private DetectionSession session;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(HandlerConstants.FRAMER, new AllButStartTlsLineBasedChannelHandler(channel.pipeline(), 1024, false, "starttls"));
        channel.pipeline().addLast(HandlerConstants.CORE_HANDLER, new ChannelInboundHandlerAdapter());
        session = new DetectionSession();
        channel.attr(AttributeKey.<CommandDetectionSession>valueOf("session")).set(session);
    }

    private static ByteBuf line(String line) {
        return Unpooled.copiedBuffer(line, StandardCharsets.US_ASCII);
    }

    @Test
    void linesReadBeforeTheTlsHandlerShouldBeRejected() {
        channel.writeInbound(line("STARTTLS\r\n"));

        assertThatThrownBy(() -> channel.writeInbound(line("RSET\r\n")))
            .matches(e -> e instanceof CommandInjectionDetectedException || e.getCause() instanceof CommandInjectionDetectedException);
    }

    @Test
    void linesReadOnceTheTlsHandlerIsInstalledShouldBeAccepted() {
        channel.writeInbound(line("STARTTLS\r\n"));

        AllButStartTlsLineBasedChannelHandler.fireTlsHandlerInstalled(channel);
        channel.writeInbound(line("RSET\r\n"));

        assertThat(session.needsCommandInjectionDetection()).isFalse();
        assertThat(channel.inboundMessages()).hasSize(2);
    }

    @Test
    void aLineReceivedByteByByteShouldBeSearchedOnce() {
        AllButStartTlsLineBasedChannelHandler handler = new AllButStartTlsLineBasedChannelHandler(null, 65536, false, "starttls");
        // Not recording the invocations: a regression would search the buffer a lot
        ByteBuf buffer = mock(UnpooledHeapByteBuf.class, withSettings()
            .spiedInstance(new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, 16, 65536))
            .defaultAnswer(CALLS_REAL_METHODS)
            .stubOnly());
        // bytesBefore searches through indexOf too
        AtomicLong searchedBytes = new AtomicLong();
        doAnswer(invocation -> {
            searchedBytes.addAndGet((int) invocation.getArgument(1) - (int) invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(buffer).indexOf(anyInt(), anyInt(), anyByte());
        int length = 4_000;

        for (int i = 0; i < length; i++) {
            buffer.writeByte('x');
            assertThat(handler.bytesBeforeLineEnd(buffer)).isEqualTo(-1);
        }
        buffer.writeByte('\n');

        assertThat(handler.bytesBeforeLineEnd(buffer)).isEqualTo(length);
        assertThat(searchedBytes.get()).isLessThanOrEqualTo(length + 1);
    }

    private static boolean isCommandInjection(Throwable e) {
        return e instanceof CommandInjectionDetectedException || e.getCause() instanceof CommandInjectionDetectedException;
    }

    private List<String> frames() {
        List<String> frames = new ArrayList<>();
        ByteBuf frame;
        while ((frame = channel.readInbound()) != null) {
            frames.add(frame.toString(StandardCharsets.US_ASCII));
            frame.release();
        }
        return frames;
    }

    @Test
    void manyLinesInASingleSegmentShouldBeReadOnce() throws Exception {
        AllButStartTlsLineBasedChannelHandler handler = (AllButStartTlsLineBasedChannelHandler) channel.pipeline().get(HandlerConstants.FRAMER);
        ChannelHandlerContext ctx = channel.pipeline().context(HandlerConstants.FRAMER);
        int count = 10_000;
        byte[] input = "NOOP\r\n".repeat(count).getBytes(StandardCharsets.US_ASCII);
        // Not recording the invocations: a regression would read the buffer a lot
        ByteBuf buffer = mock(UnpooledHeapByteBuf.class, withSettings()
            .spiedInstance(new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, input.length, input.length))
            .defaultAnswer(CALLS_REAL_METHODS)
            .stubOnly());
        AtomicLong readBytes = new AtomicLong();
        doAnswer(invocation -> {
            readBytes.addAndGet((int) invocation.getArgument(1));
            return invocation.callRealMethod();
        }).when(buffer).toString(anyInt(), anyInt(), any(Charset.class));
        buffer.writeBytes(input);

        int frames = 0;
        Object frame = handler.decode(ctx, buffer);
        while (frame != null) {
            frames++;
            ((ByteBuf) frame).release();
            frame = handler.decode(ctx, buffer);
        }

        assertThat(frames).isEqualTo(count);
        assertThat(readBytes.get()).isLessThanOrEqualTo(2L * input.length);
    }

    @Test
    void manyLinesInASingleSegmentShouldBeFramedInLinearTime() {
        int count = 100_000;

        long start = System.nanoTime();
        channel.writeInbound(line("NOOP\r\n".repeat(count)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Scanning the whole read buffer for each line takes minutes
        assertThat(frames()).hasSize(count);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void pipelinedCommandsShouldBeFramed() {
        channel.writeInbound(line("EHLO example.com\r\nMAIL FROM:<a@example.com>\r\nRCPT TO:<b@example.com>\r\nDATA\r\n"));

        assertThat(frames()).containsExactly("EHLO example.com\r\n", "MAIL FROM:<a@example.com>\r\n", "RCPT TO:<b@example.com>\r\n", "DATA\r\n");
    }

    @Test
    void linesSplitAcrossSegmentsShouldBeFramed() {
        channel.writeInbound(line("NO"));
        channel.writeInbound(line("OP\r\nRS"));
        channel.writeInbound(line("ET\r"));
        channel.writeInbound(line("\nQUIT\r\n"));

        assertThat(frames()).containsExactly("NOOP\r\n", "RSET\r\n", "QUIT\r\n");
    }

    @Test
    void startTlsFollowedByCommandsInTheSameSegmentShouldBeRejected() {
        assertThatThrownBy(() -> channel.writeInbound(line("STARTTLS\r\nRSET\r\nMAIL FROM:<a@example.com>\r\n")))
            .matches(AllButStartTlsLineBasedChannelHandlerTest::isCommandInjection);
        assertThat(frames()).isEmpty();
    }

    @Test
    void startTlsFollowedByCommandsAfterManyLinesInTheSameSegmentShouldBeRejected() {
        assertThatThrownBy(() -> channel.writeInbound(line("NOOP\r\n".repeat(1000) + "STARTTLS\r\nRSET\r\n")))
            .matches(AllButStartTlsLineBasedChannelHandlerTest::isCommandInjection);
        assertThat(frames()).isEmpty();
    }

    @Test
    void startTlsSplitAcrossSegmentsFollowedByCommandsShouldBeRejected() {
        channel.writeInbound(line("STAR"));

        assertThatThrownBy(() -> channel.writeInbound(line("TTLS\r\nRSET\r\n")))
            .matches(AllButStartTlsLineBasedChannelHandlerTest::isCommandInjection);
        assertThat(frames()).isEmpty();
    }

    @Test
    void startTlsFollowedByCommandsInTheNextSegmentShouldBeRejected() {
        channel.writeInbound(line("NOOP\r\nRSET\r\n"));
        channel.writeInbound(line("STARTTLS\r\n"));

        assertThatThrownBy(() -> channel.writeInbound(line("RSET\r\nQUIT\r\n")))
            .matches(AllButStartTlsLineBasedChannelHandlerTest::isCommandInjection);
        assertThat(frames()).containsExactly("NOOP\r\n", "RSET\r\n", "STARTTLS\r\n");
    }

    @Test
    void startTlsWithArgumentsShouldBeRejected() {
        assertThatThrownBy(() -> channel.writeInbound(line("STARTTLS RSET\r\n")))
            .matches(AllButStartTlsLineBasedChannelHandlerTest::isCommandInjection);
    }
}
