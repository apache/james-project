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
import java.util.Optional;

import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.util.MDCBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;

public class SMTPMDCContextFactory implements ProtocolMDCContextFactory {

    public Closeable from(Protocol protocol, ChannelHandlerContext ctx) {
        return MDCBuilder.create()
            .addContext(ProtocolMDCContextFactory.mdcContext(protocol, ctx))
            .addContext(from(ctx.getAttachment()))
            .build();
    }

    public static MDCBuilder forSession(SMTPSession smtpSession) {
        return MDCBuilder.create()
            .addContext(ProtocolMDCContextFactory.forSession(smtpSession))
            .addContext(forSMTPSession(smtpSession));
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
            .addContext("ehlo", smtpSession.getAttachment(SMTPSession.CURRENT_HELO_NAME, ProtocolSession.State.Connection))
            .addContext("sender", smtpSession.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction))
            .addContext("recipients", smtpSession.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction));
    }
}
