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
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.amqp.AmqpRule;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class AmqpForwardAttachmentTest {
    private static final String FROM = "fromUser@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    
    private static final String MAIL_ATTRIBUTE = "my.attribute";
    private static final String EXCHANGE_NAME = "myExchange";
    private static final String ROUTING_KEY = "myRoutingKey";
    
    private static final byte[] TEST_ATTACHMENT_CONTENT = "Test attachment content".getBytes(StandardCharsets.UTF_8);

    public SwarmGenericContainer rabbitMqContainer = new SwarmGenericContainer(Images.RABBITMQ)
            .withAffinityToContainer();

    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public AmqpRule amqpRule = new AmqpRule(rabbitMqContainer, EXCHANGE_NAME, ROUTING_KEY);

    @Rule
    public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(rabbitMqContainer).around(amqpRule);
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    
    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.DEFAUL_MAILET_CONTAINER_CONFIGURATION
            .addProcessor(ProcessorConfiguration.transport()
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
            .build(temporaryFolder);

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

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(FROM)
                .recipient(RECIPIENT))
            .awaitSent(awaitOneMinute);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitOneMinute);

        assertThat(amqpRule.readContentAsBytes()).contains(TEST_ATTACHMENT_CONTENT);
    }

}
