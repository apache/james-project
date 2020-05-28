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

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.util.MDCBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;

public class IMAPMDCContext {
    public static Closeable from(ChannelHandlerContext ctx, ChannelLocal<Object> attributes) {
        return MDCBuilder.create()
            .addContext(from(attributes.get(ctx.getChannel())))
            .addContext(MDCBuilder.PROTOCOL, "IMAP")
            .addContext(MDCBuilder.IP, retrieveIp(ctx))
            .addContext(MDCBuilder.HOST, retrieveHost(ctx))
            .build();
    }

    private static String retrieveIp(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.getChannel().getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getAddress().getHostAddress();
        }
        return remoteAddress.toString();
    }

    private static String retrieveHost(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.getChannel().getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getHostName();
        }
        return remoteAddress.toString();
    }

    private static MDCBuilder from(Object o) {
        if (o instanceof ImapSession) {
            ImapSession imapSession = (ImapSession) o;

            return MDCBuilder.create()
                .addContext("sessionId", imapSession.sessionId().asString())
                .addContext(MDCBuilder.USER, Optional.ofNullable(imapSession.getUserName())
                    .map(Username::asString))
                .addContext(from(Optional.ofNullable(imapSession.getSelected())));
        }
        return MDCBuilder.create();
    }

    private static MDCBuilder from(Optional<SelectedMailbox> selectedMailbox) {
        return selectedMailbox
            .map(value -> MDCBuilder.create()
                .addContext("selectedMailbox", value.getMailboxId().serialize()))
            .orElse(MDCBuilder.create());
    }
}
