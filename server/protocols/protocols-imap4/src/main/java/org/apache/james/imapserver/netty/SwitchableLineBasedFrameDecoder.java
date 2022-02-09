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

import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.netty.AllButStartTlsLineBasedChannelHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

public class SwitchableLineBasedFrameDecoder extends AllButStartTlsLineBasedChannelHandler {
    public static final String PATTERN = ImapConstants.STARTTLS_COMMAND.getName().toLowerCase();

    private volatile boolean framingEnabled = true;

    public SwitchableLineBasedFrameDecoder(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter) {
        super(pipeline, maxFrameLength, stripDelimiter, PATTERN);
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (this.framingEnabled) {
            super.channelRead(ctx, msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    public synchronized void enableFraming() {
        this.framingEnabled = true;
    }

    public synchronized void disableFraming(ChannelHandlerContext ctx) {
        this.framingEnabled = false;

        if (internalBuffer().readableBytes() > 0) {
            ByteBuf spareBytes = internalBuffer().retainedDuplicate();
            internalBuffer().clear();
            ctx.fireChannelRead(spareBytes);
        }
    }

    @Override
    protected CommandDetectionSession retrieveSession(ChannelHandlerContext ctx) {
        return ctx.channel().attr(NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY).get();
    }

    @Override
    protected boolean multiPartsAndOneStartTls(List<String> parts) {
        return parts.stream()
            .map(this::removeTag)
            .anyMatch(line -> line.startsWith(PATTERN)) && parts.size() > 1;
    }

    @Override
    protected boolean hasInvalidStartTlsPart(List<String> parts) {
        return parts.stream()
            .map(this::removeTag)
            .anyMatch(line -> line.startsWith(PATTERN) && !line.endsWith(PATTERN));
    }

    protected String removeTag(String input) {
        String trimmedInput = input.trim();
        int tagEnd = input.indexOf(' ');
        if (tagEnd < 0) {
            return input;
        }
        return trimmedInput.substring(tagEnd + 1);
    }
}