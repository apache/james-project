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
import io.netty.util.ByteProcessor;


public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {
    private static final Boolean FAIL_FAST = true;
    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");
    private static final Splitter CRLF_SPLITTER = Splitter.on(CRLF_MATCHER).omitEmptyStrings();
    private static final AttributeKey<Object> ATTRIBUTE_KEY = AttributeKey.valueOf("startTlsInFlight");
    // U+FFFD REPLACEMENT CHARACTER, what US-ASCII decodes a byte above 0x7F to: matched by no pattern, holding no
    // space. Ends an input handed to hasStartTLS, so that the white spaces ending the part before it are not trimmed
    private static final String NO_MATCH = "�";
    // Continues on white spaces, as trimmed by String::trim
    private static final ByteProcessor WHITE_SPACE = value -> (value & 0xFF) <= ' ';
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
            if (parts.hasCommandInjection(buffer)) {
                throw new CommandInjectionDetectedException(readAll(buffer).trim().toLowerCase(Locale.US));
            }
            // Prevents further reads on this channel to avoid race conditions
            // Framer can accept IMAP requests sent concurrently while the channel is
            // not yet encrypted allowing man-in-the-middle attacks relying on race conditions
            // Disabling auto-read when framing STARTTLS prevents accepting other frames until STARTTLS
            // is fully active.
            if (parts.hasStartTLS(buffer)) {
                ctx.channel().attr(ATTRIBUTE_KEY).set(true);
            }
        } else if (!detection) {
            parts.reset();
        }
        int readerIndex = buffer.readerIndex();
        int readableBytes = buffer.readableBytes();
        try {
            return decodeLine(ctx, buffer);
        } finally {
            // Also when framing fails after consuming some bytes: the positions shall follow the reader index
            if (buffer.readerIndex() != readerIndex) {
                scannedWithoutLineEnd = 0;
            }
            parts.consumed(buffer, readableBytes - buffer.readableBytes());
        }
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
     * When the input holds several parts (separated by CR or LF), what is handed over is not the whole input but its
     * first part followed by a U+FFFD part, or the first part holding a space after the first part, between U+FFFD
     * parts: that is what a tag removal reads. Overrides shall treat these U+FFFD parts as opaque: they match no
     * pattern and hold no space.
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

    /**
     * The read input not consumed yet, as the STARTTLS checks see it: trimmed, lower cased and split in parts on CR
     * or LF.
     *
     * The checks only need the first significant part (holding something else than white spaces), whether a
     * significant part follows it, whether a following part starts with the pattern, and the first part holding a
     * space. These are positions found by searches resuming where the previous one stopped, and answers computed once
     * per part: each byte is read a bounded number of times, not once per line framed, and the memory used does not
     * depend on the number of parts. Positions count the bytes read since the handler creation.
     */
    private class Parts {
        // Position of the reader index
        private long position = 0;
        // The bytes before were split on CR or LF
        private long splitUpTo = 0;
        // Start of the part following the last CR or LF split
        private long partStart = 0;
        // The last significant byte, -1 when none
        private long lastSignificant = -1;
        // Start of the last part followed by a CR or LF that starts with the pattern, -1 when none
        private long lastMatching = -1;
        private final Search firstSignificant = new Search(WHITE_SPACE);
        private final Search firstPartEnd = new Search(ByteProcessor.FIND_CRLF);
        private final Search firstSpace = new Search(ByteProcessor.FIND_ASCII_SPACE);
        private final Search partWithSpaceEnd = new Search(ByteProcessor.FIND_CRLF);
        // Answers about the first part, the last part not followed by a CR or LF, and the first part holding a space,
        // for the bounds they were computed for
        private long firstChecksStart = -1;
        private long firstChecksEnd = -1;
        private boolean firstMatches;
        private boolean firstHasSpace;
        private boolean firstStartTls;
        private long onlyChecksStart = -1;
        private long onlyChecksEnd = -1;
        private boolean onlyInjection;
        private boolean onlyStartTls;
        private long tailChecksStart = -1;
        private long tailChecksEnd = -1;
        private boolean tailMatches;
        private long spaceChecksAt = -1;
        private long spaceChecksEnd = -1;
        private boolean spaceChecksLast;
        private boolean spaceStartTls;

        /**
         * Searches the first byte the processor stops on, from positions that never decrease.
         */
        private class Search {
            private final ByteProcessor processor;
            private long found = -1;
            private long searchedUpTo = 0;

            Search(ByteProcessor processor) {
                this.processor = processor;
            }

            long from(ByteBuf buffer, long from) {
                if (found >= from) {
                    return found;
                }
                long start = Math.max(from, searchedUpTo);
                long end = position + buffer.readableBytes();
                int index = start < end ? buffer.forEachByte(toIndex(buffer, start), (int) (end - start), processor) : -1;
                if (index < 0) {
                    found = -1;
                    searchedUpTo = Math.max(searchedUpTo, end);
                    return -1;
                }
                found = toPosition(buffer, index);
                searchedUpTo = found;
                return found;
            }

            void reset() {
                found = -1;
                searchedUpTo = position;
            }
        }

        void consumed(ByteBuf buffer, int bytes) {
            position += bytes;
            if (bytes > 0 && position < splitUpTo) {
                byte previous = buffer.getByte(buffer.readerIndex() - 1);
                if (previous != '\n' && previous != '\r') {
                    // Not consumed up to a CR or a LF, eg by a decodeLine override failing after reading part of a
                    // line (the framers in use consume whole lines, or the whole buffer): the part split around the
                    // reader index is not the one the checks now see, split again
                    reset();
                }
            }
        }

        void reset() {
            splitUpTo = position;
            partStart = position;
            lastSignificant = -1;
            lastMatching = -1;
            firstSignificant.reset();
            firstPartEnd.reset();
            firstSpace.reset();
            partWithSpaceEnd.reset();
        }

        void update(ByteBuf buffer) {
            long end = position + buffer.readableBytes();
            if (splitUpTo < position || splitUpTo > end) {
                // Consumed further than split, eg a too long line being discarded
                reset();
            }
            partStart = Math.max(partStart, position);
            if (splitUpTo == end) {
                return;
            }
            int index = toIndex(buffer, splitUpTo);
            int endIndex = buffer.writerIndex();
            int significant = buffer.forEachByteDesc(index, endIndex - index, WHITE_SPACE);
            if (significant >= 0) {
                lastSignificant = toPosition(buffer, significant);
            }
            int lineEnd = buffer.forEachByte(index, endIndex - index, ByteProcessor.FIND_CRLF);
            while (lineEnd >= 0) {
                long lineEndPosition = toPosition(buffer, lineEnd);
                if (lineEndPosition > partStart && startsWithPattern(lowerCased(buffer, partStart, lineEndPosition))) {
                    lastMatching = partStart;
                }
                partStart = lineEndPosition + 1;
                index = lineEnd + 1;
                lineEnd = buffer.forEachByte(index, endIndex - index, ByteProcessor.FIND_CRLF);
            }
            splitUpTo = end;
        }

        boolean hasCommandInjection(ByteBuf buffer) {
            long first = firstSignificant.from(buffer, position);
            if (first < 0) {
                return false;
            }
            long firstEnd = firstPartEnd.from(buffer, first + 1);
            if (firstEnd < 0 || lastSignificant < firstEnd) {
                checkOnlyPart(buffer, first);
                return onlyInjection;
            }
            checkFirstPart(buffer, first, firstEnd);
            return firstMatches || lastMatching > firstEnd || tailMatches(buffer);
        }

        /**
         * To be called once no command injection was found.
         */
        boolean hasStartTLS(ByteBuf buffer) {
            long first = firstSignificant.from(buffer, position);
            if (first < 0) {
                return false;
            }
            long firstEnd = firstPartEnd.from(buffer, first + 1);
            if (firstEnd < 0 || lastSignificant < firstEnd) {
                checkOnlyPart(buffer, first);
                return onlyStartTls;
            }
            checkFirstPart(buffer, first, firstEnd);
            if (firstHasSpace) {
                return firstStartTls;
            }
            long space = firstSpace.from(buffer, firstEnd + 1);
            if (space < 0 || space > lastSignificant) {
                return firstStartTls;
            }
            checkPartWithSpace(buffer, firstEnd, space);
            return spaceStartTls;
        }

        private void checkOnlyPart(ByteBuf buffer, long first) {
            long end = lastSignificant + 1;
            if (onlyChecksStart == first && onlyChecksEnd == end) {
                return;
            }
            String part = lowerCased(buffer, first, end);
            onlyInjection = startsWithPattern(part) && !endsWithPattern(part);
            onlyStartTls = AllButStartTlsLineBasedChannelHandler.this.hasStartTLS(part);
            onlyChecksStart = first;
            onlyChecksEnd = end;
        }

        private void checkFirstPart(ByteBuf buffer, long first, long firstEnd) {
            if (firstChecksStart == first && firstChecksEnd == firstEnd) {
                return;
            }
            String part = lowerCased(buffer, first, firstEnd);
            firstMatches = startsWithPattern(part);
            firstHasSpace = part.indexOf(' ') >= 0;
            firstStartTls = AllButStartTlsLineBasedChannelHandler.this.hasStartTLS(part + "\n" + NO_MATCH);
            firstChecksStart = first;
            firstChecksEnd = firstEnd;
        }

        /**
         * Whether the part not followed by a CR or LF, once others precede it, starts with the pattern.
         */
        private boolean tailMatches(ByteBuf buffer) {
            long end = position + buffer.readableBytes();
            if (partStart >= end || lastSignificant < partStart) {
                return false;
            }
            if (tailChecksStart != partStart || tailChecksEnd != end) {
                tailMatches = startsWithPattern(lowerCased(buffer, partStart, end));
                tailChecksStart = partStart;
                tailChecksEnd = end;
            }
            return tailMatches;
        }

        private void checkPartWithSpace(ByteBuf buffer, long firstEnd, long space) {
            long lineEnd = partWithSpaceEnd.from(buffer, space + 1);
            long end = lineEnd < 0 ? position + buffer.readableBytes() : lineEnd;
            boolean last = lastSignificant < end;
            if (spaceChecksAt == space && spaceChecksEnd == end && spaceChecksLast == last) {
                return;
            }
            int startIndex = buffer.forEachByteDesc(toIndex(buffer, firstEnd), (int) (space - firstEnd), ByteProcessor.FIND_CRLF);
            String part = lowerCased(buffer, toPosition(buffer, startIndex) + 1, end);
            if (last) {
                spaceStartTls = AllButStartTlsLineBasedChannelHandler.this.hasStartTLS(NO_MATCH + "\n" + part);
            } else {
                spaceStartTls = AllButStartTlsLineBasedChannelHandler.this.hasStartTLS(NO_MATCH + "\n" + part + "\n" + NO_MATCH);
            }
            spaceChecksAt = space;
            spaceChecksEnd = end;
            spaceChecksLast = last;
        }

        private String lowerCased(ByteBuf buffer, long start, long end) {
            return buffer.toString(toIndex(buffer, start), (int) (end - start), StandardCharsets.US_ASCII).toLowerCase(Locale.US);
        }

        private int toIndex(ByteBuf buffer, long streamPosition) {
            return buffer.readerIndex() + (int) (streamPosition - position);
        }

        private long toPosition(ByteBuf buffer, int index) {
            return position + index - buffer.readerIndex();
        }
    }
}
