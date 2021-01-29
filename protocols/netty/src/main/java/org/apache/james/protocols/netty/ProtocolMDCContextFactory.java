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

package org.apache.james.protocols.netty;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.util.MDCBuilder;
import org.jboss.netty.channel.ChannelHandlerContext;

public interface ProtocolMDCContextFactory {
    class Standard implements ProtocolMDCContextFactory {
        @Override
        public Closeable from(Protocol protocol, ChannelHandlerContext ctx) {
            return mdcContext(protocol, ctx).build();
        }
    }

    Closeable from(Protocol protocol, ChannelHandlerContext ctx);

    static MDCBuilder mdcContext(Protocol protocol, ChannelHandlerContext ctx) {
        return MDCBuilder.create()
            .addContext(from(ctx.getAttachment()))
            .addContext(MDCBuilder.PROTOCOL, protocol.getName())
            .addContext(MDCBuilder.IP, retrieveIp(ctx))
            .addContext(MDCBuilder.HOST, retrieveHost(ctx));
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
        return Optional.ofNullable(o)
            .filter(object -> object instanceof ProtocolSession)
            .map(object -> (ProtocolSession) object)
            .map(ProtocolMDCContextFactory::forSession)
            .orElse(MDCBuilder.create());
    }

    static MDCBuilder forSession(ProtocolSession protocolSession) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.SESSION_ID, protocolSession.getSessionID())
            .addContext(MDCBuilder.CHARSET, protocolSession.getCharset().displayName())
            .addContext(MDCBuilder.USER, Optional.ofNullable(protocolSession.getUsername()).map(Username::asString));
    }

}
