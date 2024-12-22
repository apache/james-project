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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.util.MDCBuilder;

import io.netty.channel.ChannelHandlerContext;


public interface ProtocolMDCContextFactory {
    boolean ADD_HOST_TO_MDC = Boolean.parseBoolean(System.getProperty("james.protocols.mdc.hostname", "true"));

    class Standard implements ProtocolMDCContextFactory {
        @Override
        public MDCBuilder onBound(Protocol protocol, ChannelHandlerContext ctx) {
            return mdcContext(protocol, ctx);
        }

        @Override
        public MDCBuilder withContext(ProtocolSession protocolSession) {
            return from(protocolSession);
        }
    }

    MDCBuilder onBound(Protocol protocol, ChannelHandlerContext ctx);

    MDCBuilder withContext(ProtocolSession protocolSession);

    static MDCBuilder mdcContext(Protocol protocol, ChannelHandlerContext ctx) {
        MDCBuilder mdc = MDCBuilder.create()
            .addToContext(MDCBuilder.PROTOCOL, protocol.getName())
            .addToContext(MDCBuilder.IP, retrieveIp(ctx));

        if (ADD_HOST_TO_MDC) {
            return mdc.addToContext(MDCBuilder.HOST, retrieveHost(ctx));
        }
        return mdc;
    }

    private static String retrieveIp(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getAddress().getHostAddress();
        }
        if (remoteAddress == null) {
            return "null";
        }
        return remoteAddress.toString();
    }

    private static String retrieveHost(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) remoteAddress;
            return address.getHostName();
        }
        if (remoteAddress == null) {
            return "null";
        }
        return remoteAddress.toString();
    }

    static MDCBuilder from(Object o) {
        return Optional.ofNullable(o)
            .filter(ProtocolSession.class::isInstance)
            .map(ProtocolSession.class::cast)
            .map(ProtocolMDCContextFactory::forSession)
            .orElse(MDCBuilder.create());
    }

    static MDCBuilder forSession(ProtocolSession protocolSession) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.SESSION_ID, protocolSession.getSessionID())
            .addToContextIfPresent(MDCBuilder.USER, Optional.ofNullable(protocolSession.getUsername()).map(Username::asString));
    }

}
