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

import static org.apache.james.imapserver.netty.ImapChannelUpstreamHandler.MDC_KEY;

import java.net.InetSocketAddress;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.AttributeKey;

public class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HAProxyMessageHandler.class);
    private static final AttributeKey<CommandDetectionSession> SESSION_ATTRIBUTE_KEY = AttributeKey.valueOf("ImapSession");
    public static final AttributeKey<ProxyInformation> PROXY_INFO = AttributeKey.valueOf("proxyInfo");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HAProxyMessage) {
            HAProxyMessage haproxyMsg = (HAProxyMessage) msg;

            ChannelPipeline pipeline = ctx.pipeline();
            ImapSession imapSession = (ImapSession) pipeline.channel().attr(SESSION_ATTRIBUTE_KEY).get();
            if (haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP4) || haproxyMsg.proxiedProtocol().equals(HAProxyProxiedProtocol.TCP6)) {

                ctx.channel().attr(PROXY_INFO).set(
                    new ProxyInformation(
                        new InetSocketAddress(haproxyMsg.sourceAddress(), haproxyMsg.sourcePort()),
                        new InetSocketAddress(haproxyMsg.destinationAddress(), haproxyMsg.destinationPort())));

                LOGGER.info("Connection from {} runs through {} proxy", haproxyMsg.sourceAddress(), haproxyMsg.destinationAddress());
                // Refresh MDC info to account for proxying
                MDCBuilder boundMDC = IMAPMDCContext.boundMDC(ctx);

                if (imapSession != null) {
                    imapSession.setAttribute(MDC_KEY, boundMDC);
                }
            } else {
                throw new IllegalArgumentException("Only TCP4/TCP6 are supported when using PROXY protocol.");
            }

            haproxyMsg.release();
            super.channelReadComplete(ctx);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
