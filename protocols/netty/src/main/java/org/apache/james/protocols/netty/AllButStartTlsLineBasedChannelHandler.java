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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.james.protocols.api.CommandDetectionSession;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.AttributeKey;


public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {
    private static final Boolean FAIL_FAST = true;
    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");
    private static final Splitter CRLF_SPLITTER = Splitter.on(CRLF_MATCHER).omitEmptyStrings();
    private static final AttributeKey<Object> ATTRIBUTE_KEY = AttributeKey.valueOf("startTlsInFlight");
    private final ChannelPipeline pipeline;
    private final String pattern;
    // Bytes after the reader index already searched for a line end, so that a line received in many segments is not
    // searched again from its start on each of them
    private int scannedWithoutLineEnd = 0;

    private static final AttributeKey<CommandDetectionSession> sessionAttributeKey =
            AttributeKey.valueOf("session");

    /**
     * Ends the STARTTLS in flight window opened when the STARTTLS command was framed.
     *
     * Protocol handlers call it once a command was answered without starting TLS: a refused
     * STARTTLS leaves the session as it was (RFC 3207 section 4, RFC 2595 sections 3.1 and 4,
     * RFC 5804 section 2.2), thus the following commands need to be processed.
     */
    public static void clearStartTlsInFlight(Channel channel) {
        channel.attr(ATTRIBUTE_KEY).set(false);
    }

    /**
     * Opens the STARTTLS in flight window for a STARTTLS that reached the protocol decoder
     * without being framed by this handler: further frames are refused until TLS is active.
     */
    public static void markStartTlsInFlight(Channel channel) {
        channel.attr(ATTRIBUTE_KEY).set(true);
    }

    /**
     * Event fired through the pipeline once the TLS handler is installed after an accepted STARTTLS. It follows, in
     * order, the frames read before the TLS handler: these are still checked, the later ones are not.
     */
    public enum TlsHandlerInstalled {
        INSTANCE
    }

    public static void fireTlsHandlerInstalled(Channel channel) {
        channel.pipeline().fireUserEventTriggered(TlsHandlerInstalled.INSTANCE);
    }

    public AllButStartTlsLineBasedChannelHandler(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter, String pattern) {
        super(maxFrameLength, stripDelimiter, !FAIL_FAST);
        this.pipeline = pipeline;
        this.pattern = pattern;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        CommandDetectionSession session = retrieveSession(ctx);

        if (session == null || session.needsCommandInjectionDetection()) {
            Boolean startTlsInFlight = Optional.ofNullable(ctx.channel().attr(ATTRIBUTE_KEY))
                .map(attr -> attr.get())
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
            if (startTlsInFlight) {
                throw new CommandInjectionDetectedException(readAll(buffer).trim().toLowerCase(Locale.US));
            }
        }
        if ((session == null || session.needsCommandInjectionDetection()) && bytesBeforeLineEnd(buffer) >= 0) {
            // Checked once a line is complete: until then nothing is framed, and a partial line is not read again
            // on each segment
            String trimedLowerCasedInput = readAll(buffer).trim().toLowerCase(Locale.US);
            if (hasCommandInjection(trimedLowerCasedInput)) {
                throw new CommandInjectionDetectedException(trimedLowerCasedInput);
            }
            // Prevents further reads on this channel to avoid race conditions
            // Framer can accept IMAP requests sent concurrently while the channel is
            // not yet encrypted allowing man-in-the-middle attacks relying on race conditions
            // Disabling auto-read when framing STARTTLS prevents accepting other frames until STARTTLS
            // is fully active.
            if (hasStartTLS(trimedLowerCasedInput)) {
                ctx.channel().attr(ATTRIBUTE_KEY).set(true);
            }
        }
        int readerIndex = buffer.readerIndex();
        Object frame = decodeLine(ctx, buffer);
        if (buffer.readerIndex() != readerIndex) {
            scannedWithoutLineEnd = 0;
        }
        return frame;
    }

    /**
     * The number of bytes before the next line end, -1 when there is none yet. Only the bytes received since the
     * previous search are searched: to be called on the buffer being decoded only, and before consuming from it.
     */
    protected final int bytesBeforeLineEnd(ByteBuf buffer) {
        int searchFrom = buffer.readerIndex() + Math.min(scannedWithoutLineEnd, buffer.readableBytes());
        int lineEnd = buffer.indexOf(searchFrom, buffer.writerIndex(), (byte) '\n');
        if (lineEnd < 0) {
            scannedWithoutLineEnd = buffer.readableBytes();
            return -1;
        }
        scannedWithoutLineEnd = lineEnd - buffer.readerIndex();
        return lineEnd - buffer.readerIndex();
    }

    /**
     * Frames the next line, once the STARTTLS checks passed.
     */
    protected Object decodeLine(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        return super.decode(ctx, buffer);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == TlsHandlerInstalled.INSTANCE) {
            CommandDetectionSession session = retrieveSession(ctx);
            if (session != null) {
                session.stopDetectingCommandInjection();
            }
            clearStartTlsInFlight(ctx.channel());
        }
        super.userEventTriggered(ctx, evt);
    }

    protected CommandDetectionSession retrieveSession(ChannelHandlerContext ctx) {
        return pipeline.context(HandlerConstants.CORE_HANDLER).channel().attr(sessionAttributeKey).get();
    }

    protected boolean hasStartTLS(String trimedLowerCasedInput) {
        return CRLF_SPLITTER.splitToStream(trimedLowerCasedInput)
            .anyMatch(s -> s.equalsIgnoreCase(pattern));
    }

    private String readAll(ByteBuf buffer) {
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private boolean hasCommandInjection(String trimedLowerCasedInput) {
        List<String> parts = CRLF_SPLITTER.splitToList(trimedLowerCasedInput);

        return hasInvalidStartTlsPart(parts) || multiPartsAndOneStartTls(parts);
    }

    protected boolean multiPartsAndOneStartTls(List<String> parts) {
        return parts.stream()
            .anyMatch(line -> line.startsWith(pattern)) && parts.size() > 1;
    }

    protected boolean hasInvalidStartTlsPart(List<String> parts) {
        return parts.stream()
            .anyMatch(line -> line.startsWith(pattern) && !line.endsWith(pattern));
    }
}
