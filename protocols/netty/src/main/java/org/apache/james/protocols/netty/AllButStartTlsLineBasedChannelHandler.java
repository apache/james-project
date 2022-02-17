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

import org.apache.james.protocols.api.CommandDetectionSession;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.AttributeKey;


public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {
    private static final Boolean FAIL_FAST = true;
    private final ChannelPipeline pipeline;
    private final String pattern;

    private static final AttributeKey<CommandDetectionSession> sessionAttributeKey =
            AttributeKey.valueOf("session");

    public AllButStartTlsLineBasedChannelHandler(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter, String pattern) {
        super(maxFrameLength, stripDelimiter, !FAIL_FAST);
        this.pipeline = pipeline;
        this.pattern = pattern;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        CommandDetectionSession session = retrieveSession(ctx);

        if (session == null || session.needsCommandInjectionDetection()) {
            String trimedLowerCasedInput = readAll(buffer).trim().toLowerCase(Locale.US);
            if (hasCommandInjection(trimedLowerCasedInput)) {
                throw new CommandInjectionDetectedException();
            }
        }
        return super.decode(ctx, buffer);
    }

    protected CommandDetectionSession retrieveSession(ChannelHandlerContext ctx) {
        return pipeline.context(HandlerConstants.CORE_HANDLER).channel().attr(sessionAttributeKey).get();
    }

    private String readAll(ByteBuf buffer) {
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private boolean hasCommandInjection(String trimedLowerCasedInput) {
        List<String> parts = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings()
            .splitToList(trimedLowerCasedInput);

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
