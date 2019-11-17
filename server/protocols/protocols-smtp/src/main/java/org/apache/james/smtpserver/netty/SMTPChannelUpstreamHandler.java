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

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.netty.BasicChannelUpstreamHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.smtpserver.SMTPConstants;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.MessageEvent;

/**
 * {@link ChannelUpstreamHandler} which is used by the SMTPServer
 */
@Sharable
public class SMTPChannelUpstreamHandler extends BasicChannelUpstreamHandler {

    private final SmtpMetrics smtpMetrics;

    public SMTPChannelUpstreamHandler(Protocol protocol, Encryption encryption, SmtpMetrics smtpMetrics) {
        super(protocol, encryption);
        this.smtpMetrics = smtpMetrics;
    }

    public SMTPChannelUpstreamHandler(Protocol protocol, SmtpMetrics smtpMetrics) {
        super(protocol);
        this.smtpMetrics = smtpMetrics;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelConnected(ctx, e);
        smtpMetrics.getConnectionMetric().increment();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.messageReceived(ctx, e);
        smtpMetrics.getCommandsMetric().increment();
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
        smtpMetrics.getConnectionMetric().decrement();
    }

    /**
     * Cleanup temporary files
     */
    @Override
    protected void cleanup(ChannelHandlerContext ctx) {
        // Make sure we dispose everything on exit on session close
        SMTPSession smtpSession = (SMTPSession) ctx.getAttachment();

        if (smtpSession != null) {
            LifecycleUtil.dispose(smtpSession.getAttachment(SMTPConstants.MAIL, State.Transaction));
            LifecycleUtil.dispose(smtpSession.getAttachment(SMTPConstants.DATA_MIMEMESSAGE_STREAMSOURCE, State.Transaction));
        }

        super.cleanup(ctx);
    }
}
