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

package org.apache.james.protocols.smtp.core;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.util.MDCBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;

public class SMTPMDCContextFactory implements ProtocolMDCContextFactory {

    public Closeable from(Protocol protocol, ChannelHandlerContext ctx) {
        return MDCBuilder.create()
            .addToContext(ProtocolMDCContextFactory.mdcContext(protocol, ctx))
            .addToContext(from(ctx.getAttachment()))
            .build();
    }

    public static MDCBuilder forSession(SMTPSession smtpSession) {
        return MDCBuilder.create()
            .addToContext(ProtocolMDCContextFactory.forSession(smtpSession))
            .addToContext(forSMTPSession(smtpSession));
    }

    private MDCBuilder from(Object o) {
        return Optional.ofNullable(o)
            .filter(object -> object instanceof SMTPSession)
            .map(object -> (SMTPSession) object)
            .map(SMTPMDCContextFactory::forSMTPSession)
            .orElse(MDCBuilder.create());
    }

    private static MDCBuilder forSMTPSession(SMTPSession smtpSession) {
        return MDCBuilder.create()
            .addToContextIfPresent("ehlo", smtpSession.getAttachment(SMTPSession.CURRENT_HELO_NAME, ProtocolSession.State.Connection))
            .addToContextIfPresent("sender", smtpSession.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction)
                .map(MaybeSender::asString))
            .addToContextIfPresent("recipients", smtpSession.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction)
                .map(Objects::toString));
    }
}
