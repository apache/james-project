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
package org.apache.james.smtpserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.SimpleMailRepositoryLoader;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.smtpserver.netty.SmtpMetricsImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;

import com.google.common.collect.ImmutableList;
import com.google.inject.TypeLiteral;

class SMTPServerTestSystem {
    public static final String LOCAL_DOMAIN = "example.local";
    public static final Username BOB = Username.of("bob@localhost");
    public static final String PASSWORD = "bobpwd";
    public static final Instant DATE = Instant.parse("2023-04-14T10:00:00.00Z");
    public static final Clock CLOCK = Clock.fixed(DATE, ZoneId.of("Z"));

    MemoryDomainList domainList;
    MemoryUsersRepository usersRepository;
    SMTPServerTest.AlterableDNSServer dnsServer;
    MemoryMailRepositoryStore mailRepositoryStore;
    FileSystemImpl fileSystem;
    Configuration configuration;
    MockProtocolHandlerLoader chain;
    MemoryMailQueueFactory queueFactory;
    MemoryMailQueueFactory.MemoryCacheableMailQueue queue;

    SMTPServer smtpServer;
    MemoryRecipientRewriteTable rewriteTable;

    void setUp(String configuration) throws Exception {
        setUp(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream(configuration)),
            (userId, otherUserId) -> Authorizator.AuthorizationState.ALLOWED);
    }

    void setUp(HierarchicalConfiguration<ImmutableNode> configuration, Authorizator authorizator) throws Exception {
        preSetUp(authorizator);

        smtpServer.configure(configuration);
        smtpServer.init();
    }

    void preSetUp(Authorizator authorizator) throws Exception {
        domainList = new MemoryDomainList(new InMemoryDNSService());
        domainList.configure(DomainListConfiguration.DEFAULT);

        domainList.addDomain(Domain.of(LOCAL_DOMAIN));
        domainList.addDomain(Domain.of("examplebis.local"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, PASSWORD);

        createMailRepositoryStore();

        setUpFakeLoader(authorizator);
        setUpSMTPServer();
    }

    void preSetUp() throws Exception {
        preSetUp((userId, otherUserId) -> Authorizator.AuthorizationState.ALLOWED);
    }

    protected void createMailRepositoryStore() throws Exception {
        configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        MemoryMailRepositoryUrlStore urlStore = new MemoryMailRepositoryUrlStore();

        MailRepositoryStoreConfiguration configuration = MailRepositoryStoreConfiguration.forItems(
            new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));

        mailRepositoryStore = new MemoryMailRepositoryStore(urlStore, new SimpleMailRepositoryLoader(), configuration);
        mailRepositoryStore.init();
    }

    protected SMTPServer createSMTPServer(SmtpMetricsImpl smtpMetrics) {
        return new SMTPServer(smtpMetrics);
    }

    protected void setUpSMTPServer() {
        SmtpMetricsImpl smtpMetrics = mock(SmtpMetricsImpl.class);
        when(smtpMetrics.getCommandsMetric()).thenReturn(mock(Metric.class));
        when(smtpMetrics.getConnectionMetric()).thenReturn(mock(Metric.class));
        smtpServer = createSMTPServer(smtpMetrics);
        smtpServer.setDnsService(dnsServer);
        smtpServer.setFileSystem(fileSystem);
        smtpServer.setProtocolHandlerLoader(chain);
    }

    protected void setUpFakeLoader(Authorizator authorizator) {
        dnsServer = new SMTPServerTest.AlterableDNSServer();

        rewriteTable = new MemoryRecipientRewriteTable();
        rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(rewriteTable);
        CanSendFrom canSendFrom = new CanSendFromImpl(rewriteTable, aliasReverseResolver);
        queueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory(), CLOCK);
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);

        chain = MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(DomainList.class).toInstance(domainList))
            .put(binder -> binder.bind(Clock.class).toInstance(CLOCK))
            .put(binder -> binder.bind(new TypeLiteral<MailQueueFactory<?>>() {}).toInstance(queueFactory))
            .put(binder -> binder.bind(RecipientRewriteTable.class).toInstance(rewriteTable))
            .put(binder -> binder.bind(CanSendFrom.class).toInstance(canSendFrom))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(MailRepositoryStore.class).toInstance(mailRepositoryStore))
            .put(binder -> binder.bind(DNSService.class).toInstance(dnsServer))
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MetricFactory.class).to(RecordingMetricFactory.class))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP))
            .put(binder -> binder.bind(Authorizator.class).toInstance(authorizator))
            .build();
    }

    InetSocketAddress getBindedAddress() {
        return new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
    }
}
