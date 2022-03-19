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

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ConditionStep.anyInput;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ConditionStep.inputContaining;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ResponseStep.serviceNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mock.smtp.server.ConfigurationClient;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.ToRepository;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class RemoteDeliveryOnSuccessTest {
    public static final MailRepositoryUrl SUCCESS_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/success/");

    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT1 = "touser1@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT2 = "touser2@" + ANOTHER_DOMAIN;


    private static MailAddress RECIPIENT1_ADDRESS;
    private static MailAddress RECIPIENT2_ADDRESS;

    private InMemoryDNSService inMemoryDNSService;
    private ConfigurationClient mockSMTP1Configuration;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public static MockSmtpServerExtension mockSmtp1 = new MockSmtpServerExtension();

    private TemporaryJamesServer jamesServer;

    @BeforeAll
    static void setUpClass() throws AddressException {
        RECIPIENT1_ADDRESS = new MailAddress(RECIPIENT1);
        RECIPIENT2_ADDRESS = new MailAddress(RECIPIENT2);
    }

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp1.getMockSmtp().getIPAddress());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(ProcessorConfiguration.builder()
                    .enableJmx(false)
                    .state("success")
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(ToRepository.class)
                        .addProperty("repositoryPath", SUCCESS_REPOSITORY.asString()))
                    .build())
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);

        mockSMTP1Configuration = mockSmtp1.getMockSmtp().getConfigurationClient();

        assertThat(mockSMTP1Configuration.version()).isEqualTo("0.4");
    }

    @AfterEach
    void tearDown() {
        mockSMTP1Configuration.clearBehaviors();
        jamesServer.shutdown();
    }

    @Test
    void deliveredEmailShouldTransitViaSuccessProcessor() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .listMailKeys(SUCCESS_REPOSITORY))
                .hasSize(1));
    }

    @Test
    void deliveredEmailShouldTransitViaSuccessProcessorAfterRetry() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
            .expect(SMTPCommand.RCPT_TO)
            .matching(anyInput())
            .thenRespond(serviceNotAvailable("mock response"))
            .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .listMailKeys(SUCCESS_REPOSITORY))
                .hasSize(1));
    }

    @Test
    void partiallyDeliveredEmailsShouldTransitViaSuccessProcessor() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(inputContaining(RECIPIENT1))
                .thenRespond(serviceNotAvailable("mock response"))
                .anyTimes()
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, ImmutableList.of(RECIPIENT1, RECIPIENT2));

        MailRepositoryProbeImpl mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(mailRepositoryProbe
                .listMailKeys(SUCCESS_REPOSITORY))
                .hasSize(1));
        assertThat(mailRepositoryProbe.getMail(SUCCESS_REPOSITORY, mailRepositoryProbe.listMailKeys(SUCCESS_REPOSITORY).get(0))
            .getRecipients())
            .containsOnly(RECIPIENT2_ADDRESS);
    }

    @Test
    void partiallyDeliveredEmailsShouldTransitViaSuccessProcessorAfterRetry() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(inputContaining(RECIPIENT1))
                .thenRespond(serviceNotAvailable("mock response"))
                .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, ImmutableList.of(RECIPIENT1, RECIPIENT2));

        MailRepositoryProbeImpl mailRepositoryProbe = jamesServer.getProbe(MailRepositoryProbeImpl.class);
        awaitAtMostOneMinute.untilAsserted(() -> assertThat(mailRepositoryProbe
                .listMailKeys(SUCCESS_REPOSITORY))
                .hasSize(2));
        Mail mail1 = mailRepositoryProbe.getMail(SUCCESS_REPOSITORY, mailRepositoryProbe.listMailKeys(SUCCESS_REPOSITORY).get(0));
        Mail mail2 = mailRepositoryProbe.getMail(SUCCESS_REPOSITORY, mailRepositoryProbe.listMailKeys(SUCCESS_REPOSITORY).get(1));

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            assertThat(mail1.getRecipients()).hasSize(1);
            assertThat(mail2.getRecipients()).hasSize(1);
            assertThat(ImmutableList.builder()
                .addAll(mail1.getRecipients())
                .addAll(mail2.getRecipients())
                .build())
                .containsOnly(RECIPIENT1_ADDRESS, RECIPIENT2_ADDRESS);
        }));
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
            .addMailet(MailetConfiguration.builder()
                .mailet(RemoteDelivery.class)
                .matcher(All.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "3 * 10 ms")
                .addProperty("maxRetries", "3")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true")
                .addProperty("onSuccess", "success"));
    }
}
