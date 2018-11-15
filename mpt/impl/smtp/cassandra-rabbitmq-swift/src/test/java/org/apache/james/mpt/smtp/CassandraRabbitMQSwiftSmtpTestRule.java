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

import java.util.Iterator;
import java.util.function.Function;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backend.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.TestSwiftBlobStoreModule;
import org.apache.james.modules.blobstore.BlobStoreChoosingConfiguration;
import org.apache.james.modules.blobstore.BlobStoreChoosingModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.modules.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.monitor.SystemLoggingMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.Host;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraRabbitMQSwiftSmtpTestRule implements TestRule, SmtpHostSystem {

    enum SmtpServerConnectedType {
        SMTP_GLOBAL_SERVER(probe -> Port.of(probe.getSmtpPort())),
        SMTP_START_TLS_SERVER(probe -> Port.of(probe.getSmtpsPort()));

        private final Function<SmtpGuiceProbe, Port> portExtractor;

        SmtpServerConnectedType(Function<SmtpGuiceProbe, Port> portExtractor) {
            this.portExtractor = portExtractor;
        }

        public Function<SmtpGuiceProbe, Port> getPortExtractor() {
            return portExtractor;
        }
    }

    private static final Module SMTP_PROTOCOL_MODULE = Modules.combine(
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    private final Host cassandraHost;
    private final SmtpServerConnectedType smtpServerConnectedType;

    private TemporaryFolder folder;
    private GuiceJamesServer jamesServer;
    private InMemoryDNSService inMemoryDNSService;
    private ExternalSessionFactory sessionFactory;

    public CassandraRabbitMQSwiftSmtpTestRule(SmtpServerConnectedType smtpServerConnectedType, Host cassandraHost) {
        this.smtpServerConnectedType = smtpServerConnectedType;
        this.cassandraHost = cassandraHost;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return base;
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
        jamesServer = createJamesServer();
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

    private GuiceJamesServer createJamesServer() throws Exception {
        Configuration configuration = Configuration.builder()
            .workingDirectory(folder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(
                Modules
                    .override(Modules.combine(CassandraJamesServerMain.CASSANDRA_SERVER_CORE_MODULE))
                    .with(new RabbitMQModule(), new BlobStoreChoosingModule()),
                SMTP_PROTOCOL_MODULE,
                binder -> binder.bind(MailQueueItemDecoratorFactory.class).to(RawMailQueueItemDecoratorFactory.class),
                binder -> binder.bind(CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
                    .toInstance(DefaultConfigurationBuilder::new))
            .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
            .overrideWith(new TestSwiftBlobStoreModule())
            .overrideWith(binder -> binder.bind(BlobStoreChoosingConfiguration.class).toInstance(BlobStoreChoosingConfiguration.objectStorage()))
            .overrideWith(
                binder -> binder.bind(ClusterConfiguration.class).toInstance(
                    ClusterConfiguration.builder()
                        .host(cassandraHost)
                        .keyspace("testing")
                        .replicationFactor(1)
                        .build()),
                binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService));
    }

    private void createSessionFactory() {
        SmtpGuiceProbe smtpProbe = jamesServer.getProbe(SmtpGuiceProbe.class);
        Port smtpPort = smtpServerConnectedType.getPortExtractor().apply(smtpProbe);

        sessionFactory = new ExternalSessionFactory("localhost", smtpPort, new SystemLoggingMonitor(), "220 mydomain.tld smtp");
    }
}
