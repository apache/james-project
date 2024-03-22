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
package org.apache.james.modules.protocols;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.utils.GuiceProbe;


public class ImapGuiceProbe implements GuiceProbe {

    private final IMAPServerFactory imapServerFactory;

    @Inject
    private ImapGuiceProbe(IMAPServerFactory imapServerFactory) {
        this.imapServerFactory = imapServerFactory;
    }

    @PreDestroy
    void destroy() {
        imapServerFactory.destroy();
    }

    public int getImapPort() {
        return getPort(Predicate.not(AbstractConfigurableAsyncServer::getStartTLSSupported))
            .orElseThrow(() -> new IllegalStateException("IMAP server not defined"));
    }

    public int getImapStartTLSPort() {
        return getPort(AbstractConfigurableAsyncServer::getStartTLSSupported)
            .orElseThrow(() -> new IllegalStateException("IMAPS server not defined"));
    }

    public int getImapSSLPort() {
        return getPort(server -> server.getSocketType().equals("secure"))
            .orElseThrow(() -> new IllegalStateException("IMAPS server not defined"));
    }

    public Optional<Integer> getPort(Predicate<? super AbstractConfigurableAsyncServer> filter) {
        return imapServerFactory.getServers().stream()
            .filter(filter)
            .findFirst()
            .flatMap(server -> server.getListenAddresses().stream().findFirst())
            .map(InetSocketAddress::getPort);
    }
}
