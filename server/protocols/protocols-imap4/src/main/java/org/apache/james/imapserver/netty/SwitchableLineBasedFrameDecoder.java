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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.netty.AllButStartTlsLineBasedChannelHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

public class SwitchableLineBasedFrameDecoder extends AllButStartTlsLineBasedChannelHandler {
    public static final String PATTERN = ImapConstants.STARTTLS_COMMAND.getName().toLowerCase();

    private final int maxFrameLength;
    private final boolean stripDelimiter;

    public SwitchableLineBasedFrameDecoder(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter) {
        super(pipeline, maxFrameLength, stripDelimiter, PATTERN);
        this.maxFrameLength = maxFrameLength;
        this.stripDelimiter = stripDelimiter;
    }

    /**
     * A line longer than maxFrameLength is handed over as is (or what was received of it), for
     * {@link ImapRequestFrameDecoder} to reject it, as it does for a line that was not framed (eg one following a
     * literal in its segment), and to skip a literal announced at its end.
     *
     * Lines are framed here rather than by {@link io.netty.handler.codec.LineBasedFrameDecoder}: the parent keeps
     * the offset where it stopped searching for an end of line, which a hand over would make stale.
     */
    @Override
    protected Object decodeLine(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        int endOfLine = bytesBeforeLineEnd(buffer);
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
        if (stripDelimiter) {
            ByteBuf frame = buffer.readRetainedSlice(lineLength);
            buffer.skipBytes(endOfLine + 1 - lineLength);
            return frame;
        }
        return buffer.readRetainedSlice(endOfLine + 1);
    }

    @Override
    protected CommandDetectionSession retrieveSession(ChannelHandlerContext ctx) {
        return ctx.channel().attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).get();
    }

    @Override
    protected boolean startsWithPattern(String part) {
        return removeTag(part).startsWith(PATTERN);
    }

    @Override
    protected boolean endsWithPattern(String part) {
        return removeTag(part).endsWith(PATTERN);
    }

    protected String removeTag(String input) {
        String trimmedInput = input.trim();
        int tagEnd = trimmedInput.indexOf(' ');
        if (tagEnd < 0) {
            return input;
        }
        return trimmedInput.substring(tagEnd + 1);
    }

    @Override
    protected boolean hasStartTLS(String trimedLowerCasedInput) {
        return super.hasStartTLS(removeTag(trimedLowerCasedInput));
    }
}