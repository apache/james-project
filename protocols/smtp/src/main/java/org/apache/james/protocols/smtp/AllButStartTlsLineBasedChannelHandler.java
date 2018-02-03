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
package org.apache.james.protocols.smtp;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.apache.james.protocols.netty.HandlerConstants;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.frame.LineBasedFrameDecoder;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;


public class AllButStartTlsLineBasedChannelHandler extends LineBasedFrameDecoder {

    private static final String STARTTLS = "starttls";
    private static final Boolean FAIL_FAST = true;
    private final ChannelPipeline pipeline;

    public AllButStartTlsLineBasedChannelHandler(ChannelPipeline pipeline, int maxFrameLength, boolean stripDelimiter) {
        super(maxFrameLength, stripDelimiter, !FAIL_FAST);
        this.pipeline = pipeline;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        SMTPSession session = (SMTPSession) pipeline.getContext(HandlerConstants.CORE_HANDLER).getAttachment();

        if (session == null || session.needsCommandInjectionDetection()) {
            String trimedLowerCasedInput = readAll(buffer).trim().toLowerCase(Locale.US);
            if (hasCommandInjection(trimedLowerCasedInput)) {
                throw new CommandInjectionDetectedException();
            }
        }
        return super.decode(ctx, channel, buffer);
    }

    private String readAll(ChannelBuffer buffer) {
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private boolean hasCommandInjection(String trimedLowerCasedInput) {
        List<String> parts = Splitter.on(CharMatcher.anyOf("\r\n")).omitEmptyStrings()
            .splitToList(trimedLowerCasedInput);

        return hasInvalidStartTlsPart(parts) || multiPartsAndOneStartTls(parts);
    }

    private boolean multiPartsAndOneStartTls(List<String> parts) {
        return parts.stream()
            .anyMatch(line -> line.startsWith(STARTTLS)) && parts.size() > 1;
    }

    private boolean hasInvalidStartTlsPart(List<String> parts) {
        return parts.stream()
            .anyMatch(line -> line.startsWith(STARTTLS) && !line.endsWith(STARTTLS));
    }
}
