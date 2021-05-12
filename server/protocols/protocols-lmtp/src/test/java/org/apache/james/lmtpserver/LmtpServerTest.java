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

package org.apache.james.lmtpserver;

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lmtpserver.netty.LMTPServerFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.awaitility.Awaitility;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.inject.name.Names;

import reactor.core.publisher.Flux;

class LmtpServerTest {
    static int getLmtpPort(LMTPServerFactory lmtpServerFactory) {
        return lmtpServerFactory.getServers().stream()
            .findFirst()
            .flatMap(server -> server.getListenAddresses().stream().findFirst())
            .map(InetSocketAddress::getPort)
            .orElseThrow(() -> new IllegalStateException("LMTP server not defined"));
    }

    private InMemoryMailboxManager mailboxManager;
    private LMTPServerFactory lmtpServerFactory;

    @BeforeEach
    void setUp()  throws Exception {
        InMemoryDNSService dnsService = new InMemoryDNSService()
            .registerMxRecord(Domain.LOCALHOST.asString(), "127.0.0.1")
            .registerMxRecord("examplebis.local", "127.0.0.1")
            .registerMxRecord("127.0.0.1", "127.0.0.1");
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());

        domainList.addDomain(Domain.of("examplebis.local"));
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        FakeAuthenticator authenticator = new FakeAuthenticator();
        mailboxManager = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build().getMailboxManager();

        usersRepository.addUser(Username.of("bob@examplebis.local"), "pwd");

        FileSystem fileSystem = new FileSystemImpl(Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build().directories());
        MemoryRecipientRewriteTable rewriteTable = new MemoryRecipientRewriteTable();
        rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(rewriteTable);
        CanSendFrom canSendFrom = new CanSendFromImpl(rewriteTable, aliasReverseResolver);
        MockProtocolHandlerLoader loader = MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(DomainList.class).toInstance(domainList))
            .put(binder -> binder.bind(RecipientRewriteTable.class).toInstance(rewriteTable))
            .put(binder -> binder.bind(CanSendFrom.class).toInstance(canSendFrom))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(DNSService.class).toInstance(dnsService))
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MetricFactory.class).to(RecordingMetricFactory.class))
            .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
            .build();
        lmtpServerFactory = new LMTPServerFactory(loader, fileSystem, new RecordingMetricFactory(), new HashedWheelTimer());

        lmtpServerFactory.configure(ConfigLoader.getConfig(ClassLoader.getSystemResourceAsStream("lmtp.xml")));
        lmtpServerFactory.init();
    }

    @AfterEach
    void tearDown() {
        lmtpServerFactory.destroy();
    }

    @Test
    void emailsShouldWellBeReceived() throws Exception {
        SocketChannel server = SocketChannel.open();
        server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));

        server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
        server.read(ByteBuffer.allocate(1024)); // needed to synchronize
        server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
        server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

        Awaitility.await()
            .untilAsserted(() -> {
                Username username = Username.of("bob@examplebis.local");
                MailboxSession systemSession = mailboxManager.createSystemSession(username);
                assertThatCode(() ->
                    assertThat(Flux.from(mailboxManager.getMailbox(MailboxPath.inbox(username), systemSession)
                        .listMessagesMetadata(MessageRange.all(), systemSession))
                        .count()
                        .block())
                        .isEqualTo(1))
                    .doesNotThrowAnyException();
            });
    }
}