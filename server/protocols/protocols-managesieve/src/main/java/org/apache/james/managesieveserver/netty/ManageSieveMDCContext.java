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

package org.apache.james.managesieveserver.netty;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.managesieve.api.Session;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.util.MDCBuilder;

import io.netty.channel.ChannelHandlerContext;

public class ManageSieveMDCContext {
    public static Closeable from(ChannelHandlerContext ctx) {
        MDCBuilder builder = MDCBuilder.create()
            .addToContext(from(ctx.channel().attr(NettyConstants.SESSION_ATTRIBUTE_KEY).get()))
            .addToContext(MDCBuilder.PROTOCOL, "MANAGE-SIEVE")
            .addToContext(MDCBuilder.SESSION_ID, ctx.channel().id().asShortText());
        addRemoteInformation(ctx, builder);
        return builder.build();
    }

    private static void addRemoteInformation(ChannelHandlerContext ctx, MDCBuilder builder) {
        Optional<ProxyInformation> proxyInformation = Optional.ofNullable(ctx.channel().attr(NettyConstants.PROXY_INFO).get());
        if (proxyInformation.isPresent()) {
            builder.addToContext(MDCBuilder.IP, proxyInformation.get().getSource().getAddress().getHostAddress());
            builder.addToContext(MDCBuilder.HOST, proxyInformation.get().getSource().getHostName());
        } else {
            builder.addToContext(MDCBuilder.IP, retrieveIp(ctx));
            builder.addToContext(MDCBuilder.HOST, retrieveHost(ctx));
        }
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

    private static MDCBuilder from(Session session) {
        return Optional.ofNullable(session)
            .map(s -> MDCBuilder.create()
                .addToContextIfPresent(MDCBuilder.USER, Optional.ofNullable(s.getUser())
                    .map(Username::asString)))
            .orElse(MDCBuilder.create());
    }
}
