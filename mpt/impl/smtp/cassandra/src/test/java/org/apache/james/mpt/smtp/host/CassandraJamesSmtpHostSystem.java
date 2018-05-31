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

package org.apache.james.mpt.smtp.host;

import java.util.Iterator;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.modules.CassandraTestModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.mpt.smtp.SmtpHostSystem;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.Host;
import org.apache.james.utils.DataProbeImpl;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraJamesSmtpHostSystem extends ExternalSessionFactory implements SmtpHostSystem {

    private static final Module SMTP_PROTOCOL_MODULE = Modules.combine(
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    private TemporaryFolder folder;

    private GuiceJamesServer jamesServer;
    private InMemoryDNSService inMemoryDNSService;
    private final Host cassandraHost;


    public CassandraJamesSmtpHostSystem(int smtpPort, Host cassandraHost) {
        super("localhost", smtpPort, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
        this.cassandraHost = cassandraHost;
    }

    @Override
    public boolean addUser(String userAtDomain, String password) throws Exception {
        Preconditions.checkArgument(userAtDomain.contains("@"), "The 'user' should contain the 'domain'");
        Iterator<String> split = Splitter.on("@").split(userAtDomain).iterator();
        split.next();
        String domain = split.next();

        createDomainIfNeeded(domain);
        jamesServer.getProbe(DataProbeImpl.class).addUser(userAtDomain, password);
        return true;
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
        jamesServer = createJamesServer();
        jamesServer.start();
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

    protected GuiceJamesServer createJamesServer() throws Exception {
        Configuration configuration = Configuration.builder()
            .workingDirectory(folder.newFolder())
            .configurationFromClasspath()
            .build();

        return new GuiceJamesServer(configuration)
            .combineWith(
                CassandraJamesServerMain.CASSANDRA_SERVER_CORE_MODULE,
                SMTP_PROTOCOL_MODULE,
                binder -> binder.bind(MailQueueItemDecoratorFactory.class).to(RawMailQueueItemDecoratorFactory.class),
                binder -> binder.bind(CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
                    .toInstance(DefaultConfigurationBuilder::new))
            .overrideWith(new CassandraTestModule(cassandraHost),
                (binder) -> binder.bind(DNSService.class).toInstance(inMemoryDNSService));
    }
}
