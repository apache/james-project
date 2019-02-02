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
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.smtpserver.netty.SMTPServerFactory;
import org.apache.james.utils.GuiceProbe;

public class SmtpGuiceProbe implements GuiceProbe {

    private final SMTPServerFactory smtpServerFactory;

    @Inject
    private SmtpGuiceProbe(SMTPServerFactory smtpServerFactory) {
        this.smtpServerFactory = smtpServerFactory;
    }

    public int getSmtpPort() {
        return getPort(server -> true);
    }

    public int getSmtpsPort() {
        return getPort(AbstractConfigurableAsyncServer::getStartTLSSupported);
    }

    public Integer getSmtpAuthRequiredPort() {
        return getPort(server -> ((SMTPServer) server).getAuthRequired() == SMTPServer.AUTH_REQUIRED);
    }

    private Integer getPort(Predicate<? super AbstractConfigurableAsyncServer> filter) {
        return smtpServerFactory.getServers().stream()
            .filter(filter)
            .findFirst()
            .flatMap(server -> server.getListenAddresses().stream().findFirst())
            .map(InetSocketAddress::getPort)
            .orElseThrow(() -> new IllegalStateException("SMTP server not defined"));
    }
}
