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

import org.jboss.netty.channel.Channel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

public enum SslEngineUtil {
    INSTANCE;

    public enum SslEngineUtilMode {
        NONE,
        REMOTE_ONLY,
        LOCAL_ONLY,
        REMOTE_LOCAL,
        LOCAL_REMOTE
    }

    //default is NONE, so behavior is same as it was if users do nothing
    private SslEngineUtilMode sslEngineUtilMode = SslEngineUtilMode.NONE;

    public void setSslEngineUtilMode(final SslEngineUtilMode sslEngineUtilMode) {
        if (sslEngineUtilMode != null) {
            this.sslEngineUtilMode = sslEngineUtilMode;
        }
    }

    public SSLEngine generateSslEngine(final Channel channel, final SSLContext sslContext) {
        if (channel == null) {
            return sslContext.createSSLEngine();
        }
        SSLEngine engine;
        switch (sslEngineUtilMode != null ? sslEngineUtilMode : SslEngineUtilMode.NONE) {
            case NONE:
                engine = sslContext.createSSLEngine();
                break;
            case REMOTE_ONLY:
                if (channel.isConnected() && channel.getRemoteAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getRemoteAddress());
                } else {
                    engine = sslContext.createSSLEngine();
                }
                break;
            case LOCAL_ONLY:
                if (channel.isBound() && channel.getLocalAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getLocalAddress());
                } else {
                    engine = sslContext.createSSLEngine();
                }
                break;
            case REMOTE_LOCAL:
                if (channel.isConnected() && channel.getRemoteAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getRemoteAddress());
                } else if (channel.isBound() && channel.getLocalAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getLocalAddress());
                } else {
                    engine = sslContext.createSSLEngine();
                }
                break;
            case LOCAL_REMOTE:
                if (channel.isBound() && channel.getLocalAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getLocalAddress());
                } else if (channel.isConnected() && channel.getRemoteAddress() != null) {
                    engine = createSslEngineFromAddress(sslContext, (InetSocketAddress) channel.getRemoteAddress());
                } else {
                    engine = sslContext.createSSLEngine();
                }
                break;
            default:
                engine = sslContext.createSSLEngine();
                break;
        }
        return engine;
    }

    private SSLEngine createSslEngineFromAddress(final SSLContext sslContext, final InetSocketAddress address) {
        if (address != null && address.getAddress() != null) {
            return sslContext.createSSLEngine(address.getAddress().getHostAddress(), address.getPort());
        } else {
            return sslContext.createSSLEngine();
        }
    }
}
