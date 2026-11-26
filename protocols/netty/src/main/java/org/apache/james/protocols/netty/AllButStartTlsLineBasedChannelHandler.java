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
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.protocols.api.CommandDetectionSession;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Suppliers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ByteProcessor;


public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {
    private static final Boolean FAIL_FAST = true;
    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");
    private static final Splitter CRLF_SPLITTER = Splitter.on(CRLF_MATCHER).omitEmptyStrings();
    private static final AttributeKey<Object> ATTRIBUTE_KEY = AttributeKey.valueOf("startTlsInFlight");
    // Matched by no pattern: ends an input handed to hasStartTLS, so that the white spaces ending the part before it
    // are not trimmed
    private static final String NO_MATCH = "�";
    private final ChannelPipeline pipeline;
    private final String pattern;
    // Bytes after the reader index already searched for a line end, so that a line received in many segments is not
    // searched again from its start on each of them
    private int scannedWithoutLineEnd = 0;
    private final Parts parts = new Parts();

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
        boolean detection = session == null || session.needsCommandInjectionDetection();

        if (detection) {
            Boolean startTlsInFlight = Optional.ofNullable(ctx.channel().attr(ATTRIBUTE_KEY))
                .map(attr -> attr.get())
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
            if (startTlsInFlight) {
                throw new CommandInjectionDetectedException(readAll(buffer).trim().toLowerCase(Locale.US));
            }
        }
        if (detection && bytesBeforeLineEnd(buffer) >= 0) {
            // Checked once a line is complete: until then nothing is framed, and a partial line is not read again
            // on each segment. The checks are about the whole read input, the lines following this one included:
            // these are split once, not once per line framed
            parts.update(buffer);
            if (parts.hasCommandInjection()) {
                throw new CommandInjectionDetectedException(readAll(buffer).trim().toLowerCase(Locale.US));
            }
            // Prevents further reads on this channel to avoid race conditions
            // Framer can accept IMAP requests sent concurrently while the channel is
            // not yet encrypted allowing man-in-the-middle attacks relying on race conditions
            // Disabling auto-read when framing STARTTLS prevents accepting other frames until STARTTLS
            // is fully active.
            if (parts.hasStartTLS()) {
                ctx.channel().attr(ATTRIBUTE_KEY).set(true);
            }
        } else if (!detection) {
            parts.reset();
        }
        int readerIndex = buffer.readerIndex();
        int readableBytes = buffer.readableBytes();
        Object frame = decodeLine(ctx, buffer);
        if (buffer.readerIndex() != readerIndex) {
            scannedWithoutLineEnd = 0;
        }
        parts.consumed(readableBytes - buffer.readableBytes());
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

    /**
     * Whether the read input, trimmed and lower cased, holds a STARTTLS command.
     *
     * When the input holds several parts (separated by CR or LF), only its first part is handed over, followed by
     * the first part holding a space when the first part does not: that is what a tag removal reads.
     */
    protected boolean hasStartTLS(String trimedLowerCasedInput) {
        return CRLF_SPLITTER.splitToStream(trimedLowerCasedInput)
            .anyMatch(s -> s.equalsIgnoreCase(pattern));
    }

    /**
     * Whether a part of the read input (between two CR or LF), lower cased, starts with the pattern. The white spaces
     * ending the part shall not change the answer.
     */
    protected boolean startsWithPattern(String part) {
        return part.startsWith(pattern);
    }

    protected boolean endsWithPattern(String part) {
        return part.endsWith(pattern);
    }

    private String readAll(ByteBuf buffer) {
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private static String trimLeading(String input) {
        int start = 0;
        while (start < input.length() && input.charAt(start) <= ' ') {
            start++;
        }
        return input.substring(start);
    }

    /**
     * A part of the read input, between two CR or LF, lower cased, with what the STARTTLS checks need to know about it
     * for each place it can take in the trimmed input. Each is computed once, when needed: the checks of the
     * following lines do not read the part again.
     */
    private class Part {
        private final long start;
        private final long end;
        // Holds something else than white spaces, thus is not trimmed away
        private final boolean significant;
        private final boolean hasSpace;
        // Starts with the pattern, as a part following the first one
        private final boolean matches;
        // As the only part of the input: trimmed
        private final Supplier<Boolean> onlyInjection;
        private final Supplier<Boolean> onlyStartTls;
        // As the first part of the input, followed by others: leading white spaces trimmed
        private final Supplier<Boolean> firstMatches;
        private final Supplier<Boolean> firstHasSpace;
        private final Supplier<Boolean> firstStartTls;
        // As the part holding the first space of the input, followed by others or not
        private final Supplier<Boolean> middleStartTls;
        private final Supplier<Boolean> lastStartTls;

        Part(long start, String lowerCased) {
            this.start = start;
            this.end = start + lowerCased.length();
            this.significant = lowerCased.chars().anyMatch(c -> c > ' ');
            this.hasSpace = lowerCased.indexOf(' ') >= 0;
            this.matches = significant && startsWithPattern(lowerCased);
            this.onlyInjection = Suppliers.memoize(() -> startsWithPattern(lowerCased.trim()) && !endsWithPattern(lowerCased.trim()));
            this.onlyStartTls = Suppliers.memoize(() -> hasStartTLS(lowerCased.trim()));
            this.firstMatches = Suppliers.memoize(() -> startsWithPattern(trimLeading(lowerCased)));
            this.firstHasSpace = Suppliers.memoize(() -> trimLeading(lowerCased).indexOf(' ') >= 0);
            this.firstStartTls = Suppliers.memoize(() -> hasStartTLS(trimLeading(lowerCased) + "\n" + NO_MATCH));
            this.middleStartTls = Suppliers.memoize(() -> hasStartTLS(NO_MATCH + "\n" + lowerCased + "\n" + NO_MATCH));
            this.lastStartTls = Suppliers.memoize(() -> hasStartTLS(NO_MATCH + "\n" + lowerCased));
        }
    }

    /**
     * The read input not consumed yet, as the STARTTLS checks see it: trimmed, lower cased and split in parts on CR
     * or LF.
     *
     * Each byte is split once, rather than once per line framed while it is in the read buffer. Positions count the
     * bytes read since the handler creation.
     */
    private class Parts {
        // Parts followed by a CR or a LF, from the first significant one
        private final ArrayDeque<Part> complete = new ArrayDeque<>();
        private final ArrayDeque<Part> completeWithSpace = new ArrayDeque<>();
        private int completeMatching = 0;
        // null when no complete part is significant
        private Part lastSignificantComplete = null;
        // What follows the last CR or LF, null when nothing
        private Part tail = null;
        // Position of the reader index
        private long position = 0;
        // Position following the last CR or LF split
        private long splitUpTo = 0;

        void consumed(int bytes) {
            position += bytes;
        }

        void reset() {
            complete.clear();
            completeWithSpace.clear();
            completeMatching = 0;
            lastSignificantComplete = null;
            tail = null;
            splitUpTo = position;
        }

        void update(ByteBuf buffer) {
            while (!complete.isEmpty() && complete.peekFirst().start < position) {
                if (complete.peekFirst().end >= position) {
                    // Not consumed up to a CR or a LF
                    reset();
                    break;
                }
                removeFirst();
            }
            long end = position + buffer.readableBytes();
            if (splitUpTo < position || splitUpTo > end) {
                reset();
            }
            int index = buffer.readerIndex() + (int) (splitUpTo - position);
            int endIndex = buffer.writerIndex();
            int lineEnd = buffer.forEachByte(index, endIndex - index, ByteProcessor.FIND_CRLF);
            while (lineEnd >= 0) {
                add(new Part(position + index - buffer.readerIndex(), lowerCased(buffer, index, lineEnd)));
                index = lineEnd + 1;
                lineEnd = buffer.forEachByte(index, endIndex - index, ByteProcessor.FIND_CRLF);
            }
            splitUpTo = position + index - buffer.readerIndex();
            if (index == endIndex) {
                tail = null;
            } else if (tail == null || tail.start != splitUpTo || tail.end != end) {
                tail = new Part(splitUpTo, lowerCased(buffer, index, endIndex));
            }
            // Leading white spaces are trimmed whatever follows them
            while (!complete.isEmpty() && !complete.peekFirst().significant) {
                removeFirst();
            }
        }

        boolean hasCommandInjection() {
            Part first = first();
            if (first == null) {
                return false;
            }
            Part last = last();
            if (first == last) {
                return first.onlyInjection.get();
            }
            int tailMatching = tail == last && tail.matches ? 1 : 0;
            int firstMatching = first.matches ? 1 : 0;
            return first.firstMatches.get() || completeMatching - firstMatching + tailMatching > 0;
        }

        /**
         * To be called once no command injection was found.
         */
        boolean hasStartTLS() {
            Part first = first();
            if (first == null) {
                return false;
            }
            Part last = last();
            if (first == last) {
                return first.onlyStartTls.get();
            }
            if (first.firstHasSpace.get()) {
                return first.firstStartTls.get();
            }
            while (!completeWithSpace.isEmpty() && completeWithSpace.peekFirst().start <= first.start) {
                completeWithSpace.removeFirst();
            }
            Part withSpace = completeWithSpace.isEmpty() && tail == last && tail.hasSpace ? tail : completeWithSpace.peekFirst();
            if (withSpace == null || withSpace.start > last.start) {
                return first.firstStartTls.get();
            }
            if (withSpace == last) {
                return withSpace.lastStartTls.get();
            }
            return withSpace.middleStartTls.get();
        }

        private Part first() {
            if (!complete.isEmpty()) {
                return complete.peekFirst();
            }
            if (tail != null && tail.significant) {
                return tail;
            }
            return null;
        }

        private Part last() {
            if (tail != null && tail.significant) {
                return tail;
            }
            return lastSignificantComplete;
        }

        private String lowerCased(ByteBuf buffer, int index, int endIndex) {
            return buffer.toString(index, endIndex - index, StandardCharsets.US_ASCII).toLowerCase(Locale.US);
        }

        private void add(Part part) {
            if (part.start == part.end) {
                return;
            }
            complete.addLast(part);
            if (part.matches) {
                completeMatching++;
            }
            if (part.significant) {
                lastSignificantComplete = part;
            }
            if (part.hasSpace) {
                completeWithSpace.addLast(part);
            }
        }

        private void removeFirst() {
            Part part = complete.removeFirst();
            if (part.matches) {
                completeMatching--;
            }
            if (part == lastSignificantComplete) {
                lastSignificantComplete = null;
            }
            while (!completeWithSpace.isEmpty() && completeWithSpace.peekFirst().start <= part.start) {
                completeWithSpace.removeFirst();
            }
        }
    }
}
