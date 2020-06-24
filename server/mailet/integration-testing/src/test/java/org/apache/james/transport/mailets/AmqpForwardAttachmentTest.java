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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.amqp.AmqpRule;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

public class AmqpForwardAttachmentTest {
    private static final String FROM = "fromUser@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    
    private static final String MAIL_ATTRIBUTE = "my.attribute";
    private static final String EXCHANGE_NAME = "myExchange";
    private static final String ROUTING_KEY = "myRoutingKey";
    
    private static final byte[] TEST_ATTACHMENT_CONTENT = "Test attachment content".getBytes(StandardCharsets.UTF_8);

    public DockerContainer rabbitMqContainer = DockerContainer.fromName(Images.RABBITMQ)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy()
            .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));

    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public AmqpRule amqpRule = new AmqpRule(rabbitMqContainer, EXCHANGE_NAME, ROUTING_KEY);

    @Rule
    public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(rabbitMqContainer).around(amqpRule);
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    
    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(StripAttachment.class)
                    .addProperty(StripAttachment.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                    .addProperty(StripAttachment.PATTERN_PARAMETER_NAME, ".*\\.txt"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(MimeDecodingMailet.class)
                    .addProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(AmqpForwardAttribute.class)
                    .addProperty(AmqpForwardAttribute.URI_PARAMETER_NAME, amqpRule.getAmqpUri())
                    .addProperty(AmqpForwardAttribute.EXCHANGE_PARAMETER_NAME, EXCHANGE_NAME)
                    .addProperty(AmqpForwardAttribute.ATTRIBUTE_PARAMETER_NAME, MAIL_ATTRIBUTE)
                    .addProperty(AmqpForwardAttribute.ROUTING_KEY_PARAMETER_NAME, ROUTING_KEY))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder.newFolder());
        jamesServer.start();

        DataProbe dataprobe = jamesServer.getProbe(DataProbeImpl.class);
        dataprobe.addDomain(DEFAULT_DOMAIN);
        dataprobe.addUser(RECIPIENT, PASSWORD);
    }

    @After
    public void tearDown() throws Exception {
        jamesServer.shutdown();
    }

    @Test
    public void stripAttachmentShouldPutAttachmentsInMailAttributeWhenConfiguredForIt() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("simple text"),
                MimeMessageBuilder.bodyPartBuilder()
                    .data(TEST_ATTACHMENT_CONTENT)
                    .disposition("attachment")
                    .filename("test.txt"))
            .setSubject("test");

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(FROM)
                .recipient(RECIPIENT));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(amqpRule.readContentAsBytes()).contains(TEST_ATTACHMENT_CONTENT);
    }

}
