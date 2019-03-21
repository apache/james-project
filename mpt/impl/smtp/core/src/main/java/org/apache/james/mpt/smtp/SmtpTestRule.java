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
package org.apache.james.mpt.smtp;

import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.User;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.common.base.Preconditions;
import com.google.inject.Module;
import com.google.inject.util.Modules;


public class SmtpTestRule implements TestRule, SmtpHostSystem {
    @FunctionalInterface
    public interface ServerBuilder {
        GuiceJamesServer build(TemporaryFolder folder, DNSService dnsService) throws Exception;
    }

    public static final Module SMTP_PROTOCOL_MODULE = Modules.combine(
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    private final SmtpGuiceProbe.SmtpServerConnectedType smtpServerConnectedType;
    private final ServerBuilder createJamesServer;
    private TemporaryFolder folder;
    private InMemoryDNSService inMemoryDNSService;
    private GuiceJamesServer jamesServer;
    private ExternalSessionFactory sessionFactory;

    public SmtpTestRule(SmtpGuiceProbe.SmtpServerConnectedType smtpServerConnectedType, ServerBuilder createJamesServer) {
        this.smtpServerConnectedType = smtpServerConnectedType;
        this.createJamesServer = createJamesServer;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                beforeTest();
                try {
                    base.evaluate();
                } finally {
                    afterTest();
                }
            }
        };
    }

    @Override
    public boolean addUser(String userAtDomain, String password) throws Exception {
        Optional<Domain> domain = User.fromUsername(userAtDomain).getDomainPart();
        Preconditions.checkArgument(domain.isPresent(), "The 'user' should contain the 'domain'");
        createDomainIfNeeded(domain.get().asString());
        jamesServer.getProbe(DataProbeImpl.class).addUser(userAtDomain, password);
        return true;
    }

    @Override
    public Session newSession(Continuation continuation) throws Exception {
        return sessionFactory.newSession(continuation);
    }

    private void createDomainIfNeeded(String domain) throws Exception {
        if (!jamesServer.getProbe(DataProbeImpl.class).containsDomain(domain)) {
            jamesServer.getProbe(DataProbeImpl.class).addDomain(domain);
        }
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addAddressMapping(user, domain, address);
    }

    @Override
    public void beforeTest() throws Exception {
        inMemoryDNSService = new InMemoryDNSService();
        folder = new TemporaryFolder();
        folder.create();
        jamesServer = createJamesServer.build(folder, inMemoryDNSService);
        jamesServer.start();

        createSessionFactory();
    }

    @Override
    public void afterTest() {
        jamesServer.stop();
        folder.delete();
    }

    @Override
    public InMemoryDNSService getInMemoryDnsService() {
        return inMemoryDNSService;
    }

    private void createSessionFactory() {
        SmtpGuiceProbe smtpProbe = jamesServer.getProbe(SmtpGuiceProbe.class);
        Port smtpPort = smtpServerConnectedType.getPortExtractor().apply(smtpProbe);

        sessionFactory = new ExternalSessionFactory("localhost", smtpPort, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
    }
}
