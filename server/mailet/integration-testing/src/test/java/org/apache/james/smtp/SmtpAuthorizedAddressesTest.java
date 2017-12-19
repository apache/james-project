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

package org.apache.james.smtp;

import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.mailets.configuration.AwaitUtils.calmlyAwait;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.SMTPIsAuthNetwork;
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

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class SmtpAuthorizedAddressesTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    public static final int IMAP_PORT = 1143;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String FROM = "fromuser@" + JAMES_APACHE_ORG;
    private static final String TO = "to@any.com";

    private final TemporaryFolder smtpFolder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer("weave/rest-smtp-sink:latest")
        .withExposedPorts(25)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy());

    @Rule
    public RuleChain chain = RuleChain.outerRule(smtpFolder).around(fakeSmtp);
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        calmlyAwait.atMost(ONE_MINUTE).until(() -> fakeSmtp.tryConnect(25));

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(80)
            .setBaseUri("http://" + fakeSmtp.getContainerIp())
            .build();
    }

    private void createJamesServer(SmtpConfiguration.Builder smtpConfiguration) throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .addProcessor(ProcessorConfiguration.root()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToProcessor.class)
                    .addProperty("processor", ProcessorConfiguration.STATE_TRANSPORT)))
            .addProcessor(CommonProcessors.error())
            .addProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(LocalDelivery.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(SMTPIsAuthNetwork.class)
                    .mailet(RemoteDelivery.class)
                    .addProperty("outgoingQueue", "outgoing")
                    .addProperty("delayTime", "5000, 100000, 500000")
                    .addProperty("maxRetries", "25")
                    .addProperty("maxDnsProblemRetries", "0")
                    .addProperty("deliveryThreads", "10")
                    .addProperty("sendpartial", "true")
                    .addProperty("bounceProcessor", ProcessorConfiguration.STATE_BOUNCES)
                    .addProperty("gateway", fakeSmtp.getContainerIp()))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToProcessor.class)
                    .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES)))
            .addProcessor(CommonProcessors.localAddressError())
            .addProcessor(CommonProcessors.relayDenied())
            .addProcessor(CommonProcessors.bounces())
            .build();
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withSmtpConfiguration(smtpConfiguration.build())
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(FROM, PASSWORD);
    }

    @After
    public void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Test
    public void userShouldBeAbleToRelayMessagesWhenInAcceptedNetwork() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("127.0.0.0/8"));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {

            messageSender.sendMessage(FROM, TO)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            calmlyAwait.atMost(ONE_MINUTE)
                .until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void userShouldNotBeAbleToRelayMessagesWhenOutOfAcceptedNetwork() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {

            messageSender.sendMessage(FROM, TO);
            calmlyAwait.atMost(ONE_MINUTE).until(messageSender::messageSendingFailed);
        }
    }

    @Test
    public void userShouldBeAbleToRelayMessagesWhenOutOfAcceptedNetworkButAuthenticated() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.authentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG, FROM, PASSWORD)) {

            messageSender.sendMessage(FROM, TO)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            calmlyAwait.atMost(ONE_MINUTE)
                .until(this::messageIsReceivedByTheSmtpServer);
        }
    }

    @Test
    public void localDeliveryShouldBePossibleFromNonAuthenticatedNonAuthorizedSender() throws Exception {
        createJamesServer(SmtpConfiguration.builder()
            .requireAuthentication()
            .withAutorizedAddresses("172.0.0.0/8"));

        try (SMTPMessageSender messageSender =
                 SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG)) {

            messageSender.sendMessage(TO, FROM)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(FROM, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
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

}
