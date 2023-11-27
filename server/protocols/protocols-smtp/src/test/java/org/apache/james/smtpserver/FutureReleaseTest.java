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

import com.google.common.collect.ImmutableList;
import com.google.inject.TypeLiteral;
import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.net.smtp.SMTPClient;
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
import org.apache.james.mailrepository.memory.*;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
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
import org.apache.james.smtpserver.netty.SmtpMetrics;
import org.apache.james.smtpserver.netty.SmtpMetricsImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FutureReleaseTest {
    public static final String LOCAL_DOMAIN = "example.local";
    public static final Username BOB = Username.of("bob@localhost");
    public static final String PASSWORD = "bobpwd";
    private static final Instant DATE = Instant.parse("2023-04-14T10:00:00.00Z");
    private static final Clock CLOCK = Clock.fixed(DATE, ZoneId.of("Z"));

    protected MemoryDomainList domainList;
    protected MemoryUsersRepository usersRepository;
    protected SMTPServerTest.AlterableDNSServer dnsServer;
    protected MemoryMailRepositoryStore mailRepositoryStore;
    protected FileSystemImpl fileSystem;
    protected Configuration configuration;
    protected MockProtocolHandlerLoader chain;
    protected MemoryMailQueueFactory queueFactory;
    protected MemoryMailQueueFactory.MemoryCacheableMailQueue queue;

    private SMTPServer smtpServer;

    @BeforeEach
    void setUp() throws Exception {
        domainList = new MemoryDomainList(new InMemoryDNSService());
        domainList.configure(DomainListConfiguration.DEFAULT);

        domainList.addDomain(Domain.of(LOCAL_DOMAIN));
        domainList.addDomain(Domain.of("examplebis.local"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, PASSWORD);

        createMailRepositoryStore();

        setUpFakeLoader();
        setUpSMTPServer();

        smtpServer.configure(FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("smtpserver-futurerelease.xml")));
        smtpServer.init();
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

    protected void setUpSMTPServer() {
        SmtpMetricsImpl smtpMetrics = mock(SmtpMetricsImpl.class);
        when(smtpMetrics.getCommandsMetric()).thenReturn(mock(Metric.class));
        when(smtpMetrics.getConnectionMetric()).thenReturn(mock(Metric.class));
        smtpServer = new SMTPServer(smtpMetrics, dnsServer, chain, fileSystem);
    }

    protected void setUpFakeLoader() {
        dnsServer = new SMTPServerTest.AlterableDNSServer();

        MemoryRecipientRewriteTable rewriteTable = new MemoryRecipientRewriteTable();
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
            .put(binder -> binder.bind(Authorizator.class).toInstance((userId, otherUserId) -> Authorizator.AuthorizationState.ALLOWED))
            .build();
    }

    @AfterEach
    void tearDown() {
        smtpServer.destroy();
    }

    @Test
    void rejectFutureReleaseUsageWhenUnauthenticated() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        smtpProtocol.sendCommand("EHLO whatever.tld");
        smtpProtocol.sendCommand("MAIL FROM: <bob@whatever.tld> HOLDFOR=83200");

        assertThat(smtpProtocol.getReplyString()).isEqualTo("554 Needs to be logged in in order to use future release extension\r\n");
    }

    @Test
    void ehloShouldAdvertiseFutureReleaseExtension() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).contains("250 FUTURERELEASE 86400 2023-04-15T10:00:00Z");
        });
    }

    @Test
    void ehloShouldNotAdvertiseFutureReleaseExtensionWhenUnauthenticated() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        smtpProtocol.sendCommand("EHLO localhost");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250 FUTURERELEASE 86400 2023-04-15T10:00:00Z");
        });
    }

    private void authenticate(SMTPClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + BOB.asString() + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
            .as("authenticated")
            .isEqualTo(235);
    }

    @Test
    void testSuccessCaseWithHoldForParams() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=83200");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        ManageableMailQueue.MailQueueIterator browse = queue.browse();
        assertThat(browse.hasNext()).isTrue();
        assertThat(browse.next().getNextDelivery().map(ChronoZonedDateTime::toInstant))
            .contains(DATE.plusSeconds(83200));
    }

    @Test
    void testSuccessCaseWithHoldUntilParams() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-14T10:30:00Z");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        ManageableMailQueue.MailQueueIterator browse = queue.browse();
        assertThat(browse.hasNext()).isTrue();
        assertThat(browse.next().getNextDelivery().map(ChronoZonedDateTime::toInstant))
            .contains(Instant.parse("2023-04-14T10:30:00Z"));
    }

    @Test
    void mailShouldBeRejectedWhenExceedingMaxFutureReleaseInterval() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=93200");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidHoldFor() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=BAD");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 Incorrect syntax when handling FUTURE-RELEASE mail parameter");
        });
    }

    @Test
    void mailShouldBeRejectedWhenInvalidHoldUntil() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=BAD");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isNotEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldUntilIsADate() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-15");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isNotEqualTo(250);
            softly.assertThat(smtpProtocol.getReplyString()).doesNotContain("250");
        });
    }

    @Test
    void mailShouldBeRejectedWhenMaxFutureReleaseDateTime() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-15T11:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor is greater than max-future-release-interval or holdUntil exceeded max-future-release-date-time");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldForIsNegative() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=-30");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeRejectedWhenHoldUntilBeforeNow() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDUNTIL=2023-04-13T05:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeRejectedWhenMailParametersContainBothHoldForAndHoldUntil() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost> HOLDFOR=83017 HOLDUNTIL=2023-04-12T11:00:00Z");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(smtpProtocol.getReplyCode()).isEqualTo(501);
            softly.assertThat(smtpProtocol.getReplyString()).contains("501 HoldFor value is negative or holdUntil value is before now");
        });
    }

    @Test
    void mailShouldBeSentWhenThereIsNoMailParameters() throws Exception {
        SMTPClient smtpProtocol = new SMTPClient();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(smtpServer).retrieveBindedAddress();
        smtpProtocol.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        authenticate(smtpProtocol);

        smtpProtocol.sendCommand("EHLO localhost");
        smtpProtocol.sendCommand("MAIL FROM: <bob@localhost>");
        smtpProtocol.sendCommand("RCPT TO:<rcpt@localhost>");
        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithFutureRelease\r\n.\r\n");

        assertThat(queue.getSize()).isEqualTo(1L);
    }
}
