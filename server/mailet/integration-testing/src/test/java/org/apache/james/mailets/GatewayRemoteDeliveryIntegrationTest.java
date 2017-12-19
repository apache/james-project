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

package org.apache.james.mailets;

import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.PostmasterAlias;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.RelayLimit;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.HostPortWaitStrategy;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class GatewayRemoteDeliveryIntegrationTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final int IMAP_PORT = 1143;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "from@" + JAMES_APACHE_ORG;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final TemporaryFolder smtpFolder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer(Images.FAKE_SMTP)
        .withExposedPorts(25)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy());

    @Rule
    public final RuleChain chain = RuleChain.outerRule(smtpFolder).around(fakeSmtp);

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;
    private DataProbe dataProbe;

    @Before
    public void setup() throws Exception {
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();

        calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> fakeSmtp.tryConnect(25));

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(80)
            .setBaseUri("http://" + fakeSmtp.getContainerIp())
            .build();
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenNoPort() throws Exception {
        String gatewayProperty = fakeSmtp.getContainerIp();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenPort() throws Exception {
        String gatewayProperty = fakeSmtp.getContainerIp() + ":25";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenSeveralIps() throws Exception {
        String gatewayProperty = fakeSmtp.getContainerIp() + ",invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void outgoingMailShouldFallbackToSecondGatewayWhenFirstInvalid() throws Exception {
        String gatewayProperty = "invalid.domain," + fakeSmtp.getContainerIp();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void outgoingMailShouldNotBeSentDirectlyToTheHostWhenGatewayFails() throws Exception {
        String gatewayProperty = "invalid.domain";
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();
        InetAddress containerIp = InetAddress.getByName(fakeSmtp.getContainerIp());
        inMemoryDNSService.registerRecord(JAMES_ANOTHER_DOMAIN, containerIp, JAMES_ANOTHER_DOMAIN);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            when()
                .get("/api/email")
            .then()
                .statusCode(200)
                .body("", hasSize(0));
        }
    }
    @Test
    public void remoteDeliveryShouldBounceUponFailure() throws Exception {
        String gatewayProperty = "invalid.domain";
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();
        InetAddress containerIp = InetAddress.getByName(fakeSmtp.getContainerIp());
        inMemoryDNSService.registerRecord(JAMES_ANOTHER_DOMAIN, containerIp, JAMES_ANOTHER_DOMAIN);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .postmaster("postmaster@" + JAMES_APACHE_ORG)
                .addProcessor(root())
                .addProcessor(CommonProcessors.error())
                .addProcessor(relayAndLocalDeliveryTransport(gatewayProperty))
                .addProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(FROM, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void remoteDeliveryShouldBounceUponFailureWhenNoBounceProcessor() throws Exception {
        String gatewayProperty = "invalid.domain";
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();
        InetAddress containerIp = InetAddress.getByName(fakeSmtp.getContainerIp());
        inMemoryDNSService.registerRecord(JAMES_ANOTHER_DOMAIN, containerIp, JAMES_ANOTHER_DOMAIN);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .postmaster("postmaster@" + JAMES_APACHE_ORG)
                .addProcessor(root())
                .addProcessor(CommonProcessors.error())
                .addProcessor(ProcessorConfiguration.transport()
                    .enableJmx(true)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RemoveMimeHeader.class)
                        .addProperty("name", "bcc"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(LocalDelivery.class))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RemoteDelivery.class)
                        .addProperty("outgoingQueue", "outgoing")
                        .addProperty("delayTime", "5000, 100000, 500000")
                        .addProperty("maxRetries", "2")
                        .addProperty("maxDnsProblemRetries", "0")
                        .addProperty("deliveryThreads", "2")
                        .addProperty("sendpartial", "true")
                        .addProperty("gateway", gatewayProperty)))
                .addProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(FROM, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void directResolutionShouldBeWellPerformed() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();
        InetAddress containerIp = InetAddress.getByName(fakeSmtp.getContainerIp());
        inMemoryDNSService.registerRecord(JAMES_ANOTHER_DOMAIN, containerIp, JAMES_ANOTHER_DOMAIN);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .postmaster("postmaster@" + JAMES_APACHE_ORG)
                .addProcessor(root())
                .addProcessor(CommonProcessors.error())
                .addProcessor(directResolutionTransport())
                .addProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {
            messageSender.sendMessage(FROM, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    private boolean messageIsReceivedByTheSmtpServer() {
        try {
            when()
                .get("/api/email")
            .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].from", equalTo(FROM))
                .body("[0].subject", equalTo("test"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private MailetContainer generateMailetContainerConfiguration(String gatewayProperty) {
        return MailetContainer.builder()
            .postmaster("postmaster@" + JAMES_APACHE_ORG)
            .addProcessor(root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(relayOnlyTransport(gatewayProperty))
            .addProcessor(CommonProcessors.bounces())
            .build();
    }

    public ProcessorConfiguration root() {
        // Custom in memory DNS resolution is not possible combined with InSpamerBackList
        return ProcessorConfiguration.root()
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(PostmasterAlias.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RelayLimit.class)
                .matcherCondition("30")
                .mailet(Null.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", "transport"))
            .build();
    }

    private ProcessorConfiguration relayOnlyTransport(String gatewayProperty) {
        return ProcessorConfiguration.transport()
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoveMimeHeader.class)
                .addProperty("name", "bcc"))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoteDelivery.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "2")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces")
                .addProperty("gateway", gatewayProperty))
            .build();
    }

    private ProcessorConfiguration relayAndLocalDeliveryTransport(String gatewayProperty) {
        return ProcessorConfiguration.transport()
            .enableJmx(true)
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoteDelivery.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "2")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces")
                .addProperty("gateway", gatewayProperty))
            .build();
    }

    private ProcessorConfiguration directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .enableJmx(true)
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoteDelivery.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "2")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces"))
            .build();
    }

}
