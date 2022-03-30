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
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {
    private static final Boolean FAIL_FAST = true;
    private static final CharMatcher CRLF_MATCHER = CharMatcher.anyOf("\r\n");
    private static final Splitter CRLF_SPLITTER = Splitter.on(CRLF_MATCHER).omitEmptyStrings();

    private final ChannelPipeline pipeline;
    private final String pattern;

    public AllButStartTlsLineBasedChannelHandler(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter, String pattern) {
        super(maxFrameLength, stripDelimiter, !FAIL_FAST);
        this.pipeline = pipeline;
        this.pattern = pattern;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        CommandDetectionSession session = retrieveSession(ctx, channel);

        if (session == null || session.needsCommandInjectionDetection()) {
            boolean startTlsInFlight = Optional.ofNullable(ctx.getChannel().getAttachment())
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
            String trimedLowerCasedInput = readAll(buffer).trim().toLowerCase(Locale.US);
            if (hasCommandInjection(trimedLowerCasedInput) || startTlsInFlight) {
                throw new CommandInjectionDetectedException();
            }
            // Prevents further reads on this channel to avoid race conditions
            // Framer can accept IMAP requests sent concurrently while the channel is
            // not yet encrypted allowing man-in-the-middle attacks relying on race conditions
            // Disabling auto-read when framing STARTTLS prevents accepting other frames until STARTTLS
            // is fully active.
            if (hasStartTLS(trimedLowerCasedInput)) {
                ctx.getChannel().setAttachment(true);
            }
        }
        return super.decode(ctx, channel, buffer);
    }

    protected boolean hasStartTLS(String trimedLowerCasedInput) {
        List<String> parts = CRLF_SPLITTER.splitToList(trimedLowerCasedInput);

        return parts.stream().anyMatch(s -> s.equalsIgnoreCase(pattern));
    }

    protected CommandDetectionSession retrieveSession(ChannelHandlerContext ctx, Channel channel) {
        return (CommandDetectionSession) pipeline.getContext(HandlerConstants.CORE_HANDLER).getAttachment();
    }

    private String readAll(ChannelBuffer buffer) {
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
