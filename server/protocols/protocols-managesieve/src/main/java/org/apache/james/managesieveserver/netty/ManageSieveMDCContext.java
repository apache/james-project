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

import org.apache.james.managesieve.api.Session;
import org.apache.james.util.MDCBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;

public class ManageSieveMDCContext {
    public static Closeable from(ChannelHandlerContext ctx, ChannelLocal<Session> attributes) {
        return MDCBuilder.create()
            .addToContext(from(attributes.get(ctx.getChannel())))
            .addToContext(MDCBuilder.PROTOCOL, "MANAGE-SIEVE")
            .addToContext(MDCBuilder.IP, retrieveIp(ctx))
            .addToContext(MDCBuilder.HOST, retrieveHost(ctx))
            .addToContext(MDCBuilder.SESSION_ID, Integer.toString(ctx.getChannel().getId()))
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

    private static MDCBuilder from(Session session) {
        return Optional.ofNullable(session)
            .map(s -> MDCBuilder.create()
                .addToContext(MDCBuilder.USER, s.getUser().asString()))
            .orElse(MDCBuilder.create());
    }
}
