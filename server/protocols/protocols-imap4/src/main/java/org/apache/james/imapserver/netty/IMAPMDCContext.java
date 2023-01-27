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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.apache.james.util.MDCBuilder;

import io.netty.channel.ChannelHandlerContext;

public class IMAPMDCContext {
    public static MDCBuilder boundMDC(ChannelHandlerContext ctx) {
        MDCBuilder mdc = MDCBuilder.create()
            .addToContext(MDCBuilder.PROTOCOL, "IMAP");
        setIpAndProxyInformation(ctx, mdc);

        if (ProtocolMDCContextFactory.ADD_HOST_TO_MDC) {
            return mdc.addToContext(MDCBuilder.HOST, retrieveHost(ctx));
        }
        return mdc;
    }

    private static void setIpAndProxyInformation(ChannelHandlerContext ctx, MDCBuilder mdcBuilder) {
        Optional.ofNullable(ctx.channel().attr(HAProxyMessageHandler.PROXY_INFO)
            .get())
            .ifPresentOrElse(proxyInformation -> {
                    mdcBuilder.addToContext(MDCBuilder.IP, proxyInformation.getSource().toString());
                    mdcBuilder.addToContext("proxy.source", proxyInformation.getSource().toString());
                    mdcBuilder.addToContext("proxy.destination", proxyInformation.getDestination().toString());
                    mdcBuilder.addToContext("proxy.ip", retrieveIp(ctx));
                },
                () -> mdcBuilder.addToContext(MDCBuilder.IP, retrieveIp(ctx)));
    }

    private static String retrieveIp(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getAddress().getHostAddress();
        }
        return remoteAddress.toString();
    }

    private static String retrieveHost(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getHostName();
        }
        return remoteAddress.toString();
    }

    public static MDCBuilder from(ImapSession imapSession) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.USER, Optional.ofNullable(imapSession.getUserName())
                .map(Username::asString)
                .orElse(""))
            .addToContextIfPresent("selectedMailbox", Optional.ofNullable(imapSession.getSelected())
                .map(selectedMailbox -> selectedMailbox.getMailboxId().serialize()));
    }
}
