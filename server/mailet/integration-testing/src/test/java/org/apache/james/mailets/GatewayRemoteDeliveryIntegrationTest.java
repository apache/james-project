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
import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitOneMinute;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.concurrent.TimeUnit;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtpHelper;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.HostPortWaitStrategy;

import com.jayway.restassured.RestAssured;

public class GatewayRemoteDeliveryIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private final TemporaryFolder smtpFolder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer(Images.FAKE_SMTP)
        .withExposedPorts(25)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy());

    @Rule
    public final RuleChain chain = RuleChain.outerRule(smtpFolder).around(fakeSmtp);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;
    private InMemoryDNSService inMemoryDNSService;

    @Before
    public void setup() throws Exception {
        awaitOneMinute.until(() -> fakeSmtp.tryConnect(25));

        RestAssured.requestSpecification = FakeSmtpHelper.requestSpecification(fakeSmtp.getContainerIp());

        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, fakeSmtp.getContainerIp());
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
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        awaitOneMinute.until(this::messageIsReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenPort() throws Exception {
        String gatewayProperty = fakeSmtp.getContainerIp() + ":25";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        awaitOneMinute.until(this::messageIsReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldTransitThroughGatewayWhenSeveralIps() throws Exception {
        String gatewayProperty = fakeSmtp.getContainerIp() + ",invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        awaitOneMinute.until(this::messageIsReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldFallbackToSecondGatewayWhenFirstInvalid() throws Exception {
        String gatewayProperty = "invalid.domain," + fakeSmtp.getContainerIp();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        awaitOneMinute.until(this::messageIsReceivedByTheSmtpServer);
    }

    @Test
    public void outgoingMailShouldNotBeSentDirectlyToTheHostWhenGatewayFails() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        when()
            .get("/api/email")
        .then()
            .statusCode(200)
            .body("", hasSize(0));
    }

    @Test
    public void remoteDeliveryShouldBounceUponFailure() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(generateMailetContainerConfiguration(gatewayProperty))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitOneMinute);
    }

    @Test
    public void remoteDeliveryShouldBounceUponFailureWhenNoBounceProcessor() throws Exception {
        String gatewayProperty = "invalid.domain";

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .addProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.LOCAL_DELIVERY)
                    .addMailet(MailetConfiguration.remoteDeliveryBuilderNoBounces()
                        .matcher(All.class)
                        .addProperty("gateway", gatewayProperty))))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitOneMinute);
    }

    @Test
    public void directResolutionShouldBeWellPerformed() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .addProcessor(CommonProcessors.simpleRoot())
                .addProcessor(CommonProcessors.error())
                .addProcessor(directResolutionTransport())
                .addProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        awaitOneMinute.until(this::messageIsReceivedByTheSmtpServer);
    }

    private boolean messageIsReceivedByTheSmtpServer() {
        return FakeSmtpHelper.isReceived(response -> response
            .body("", hasSize(1))
            .body("[0].from", equalTo(FROM))
            .body("[0].subject", equalTo("test")));
    }

    private MailetContainer.Builder generateMailetContainerConfiguration(String gatewayProperty) {
        return TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .addProcessor(relayAndLocalDeliveryTransport(gatewayProperty));
    }

    private ProcessorConfiguration.Builder relayAndLocalDeliveryTransport(String gatewayProperty) {
        return ProcessorConfiguration.transport()
            .addMailetsFrom(CommonProcessors.deliverOnlyTransport())
            .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                .addProperty("gateway", gatewayProperty)
                .matcher(All.class));
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                .matcher(All.class));
    }


}
