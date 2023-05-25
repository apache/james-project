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

import static org.apache.james.smtpserver.netty.SMTPServer.AuthenticationAnnounceMode.FOR_UNAUTHORIZED_ADDRESSES;

import java.net.InetSocketAddress;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.smtpserver.netty.SMTPServerFactory;
import org.apache.james.util.Port;
import org.apache.james.utils.GuiceProbe;

public class SmtpGuiceProbe implements GuiceProbe {

    public enum SmtpServerConnectedType {
        SMTP_GLOBAL_SERVER(SmtpGuiceProbe::getSmtpPort),
        SMTP_START_TLS_SERVER(SmtpGuiceProbe::getSmtpStartTlsPort);

        private final Function<SmtpGuiceProbe, Port> portExtractor;

        SmtpServerConnectedType(Function<SmtpGuiceProbe, Port> portExtractor) {
            this.portExtractor = portExtractor;
        }

        public Function<SmtpGuiceProbe, Port> getPortExtractor() {
            return portExtractor;
        }
    }

    private final SMTPServerFactory smtpServerFactory;

    @Inject
    private SmtpGuiceProbe(SMTPServerFactory smtpServerFactory) {
        this.smtpServerFactory = smtpServerFactory;
    }

    public Port getSmtpPort() {
        return getPort(server -> true);
    }

    public Port getSmtpStartTlsPort() {
        return getPort(AbstractConfigurableAsyncServer::getStartTLSSupported);
    }

    public Port getSmtpSslPort() {
        return getPort(server -> {
            System.out.println(server.getServiceType());
            return server.getSocketType().equals("secure");
        });
    }

    public Port getSmtpAuthRequiredPort() {
        return getPort(server -> ((SMTPServer) server).getAuthRequired().equals(FOR_UNAUTHORIZED_ADDRESSES));
    }

    private Port getPort(Predicate<? super AbstractConfigurableAsyncServer> filter) {
        return smtpServerFactory.getServers().stream()
            .filter(filter)
            .findFirst()
            .flatMap(server -> server.getListenAddresses().stream().findFirst())
            .map(InetSocketAddress::getPort)
            .map(Port::new)
            .orElseThrow(() -> new IllegalStateException("SMTP server not defined"));
    }
}
