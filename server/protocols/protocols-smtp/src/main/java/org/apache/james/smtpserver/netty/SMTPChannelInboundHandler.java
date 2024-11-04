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
package org.apache.james.smtpserver.netty;

import java.util.Optional;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler;
import org.apache.james.protocols.netty.BasicChannelInboundHandler;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.smtp.core.SMTPMDCContextFactory;
import org.apache.james.smtpserver.ExtendedSMTPSession;
import org.apache.james.smtpserver.SMTPConstants;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;

/**
 * {@link BasicChannelInboundHandler} which is used by the SMTPServer
 */
public class SMTPChannelInboundHandler extends BasicChannelInboundHandler {

    private final SmtpMetrics smtpMetrics;
    private final ChannelGroup smtpChannelGroup;

    public SMTPChannelInboundHandler(Protocol protocol, Encryption encryption, boolean proxyRequired, SmtpMetrics smtpMetrics, ChannelGroup smtpChannelGroup) {
        super(new SMTPMDCContextFactory(), protocol, encryption, proxyRequired);
        this.smtpMetrics = smtpMetrics;
        this.smtpChannelGroup = smtpChannelGroup;
        this.resultHandlers.add(recordCommandCount(smtpMetrics));
    }


    private ProtocolHandlerResultHandler recordCommandCount(SmtpMetrics smtpMetrics) {
        return (session, response, executionTime, handler) -> {
            smtpMetrics.getCommandsMetric().increment();
            return response;
        };
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        smtpChannelGroup.add(ctx.channel());
        super.channelActive(ctx);
        smtpMetrics.getConnectionMetric().increment();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        smtpChannelGroup.remove(ctx.channel());
        super.channelInactive(ctx);
        smtpMetrics.getConnectionMetric().decrement();
    }

    /**
     * Cleanup temporary files
     */
    @Override
    protected void cleanup(ChannelHandlerContext ctx) {
        // Make sure we dispose everything on exit on session close
        ExtendedSMTPSession smtpSession = (ExtendedSMTPSession) ctx.channel().attr(SMTPConstants.SMTP_SESSION_ATTRIBUTE_KEY).get();

        if (smtpSession != null) {
            smtpSession.getAttachment(SMTPConstants.MAIL, State.Transaction).ifPresent(LifecycleUtil::dispose);
            Optional.ofNullable(smtpSession.getMimeMessageWriter()).ifPresent(LifecycleUtil::dispose);
        }

        super.cleanup(ctx);
    }
}
