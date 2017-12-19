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

import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.MailAddress;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.transport.mailets.amqp.AmqpRule;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
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

public class ContactExtractorTest {
    public static final String SENDER = "sender@" + DEFAULT_DOMAIN;
    public static final String TO = "to@" + DEFAULT_DOMAIN;
    public static final String TO2 = "to2@" + DEFAULT_DOMAIN;
    public static final String CC = "cc@" + DEFAULT_DOMAIN;
    public static final String CC2 = "cc2@" + DEFAULT_DOMAIN;
    public static final String BCC = "bcc@" + DEFAULT_DOMAIN;
    public static final String BCC2 = "bcc2@" + DEFAULT_DOMAIN;
    public static final String EXCHANGE = "collector:email";
    public static final String ROUTING_KEY = "";

    public SwarmGenericContainer rabbit = new SwarmGenericContainer(Images.RABBITMQ);
    public AmqpRule amqpRule = new AmqpRule(rabbit, EXCHANGE, ROUTING_KEY);
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public RuleChain chain = RuleChain.outerRule(rabbit).around(amqpRule).around(folder);
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        String attribute = "ExtractedContacts";
        MailetContainer mailets = MailetContainer.builder()
            .postmaster(SENDER)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(
                ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(SMTPAuthSuccessful.class)
                        .mailet(ContactExtractor.class)
                        .addProperty(ContactExtractor.Configuration.ATTRIBUTE, attribute))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(AmqpForwardAttribute.class)
                        .addProperty(AmqpForwardAttribute.URI_PARAMETER_NAME, amqpRule.getAmqpUri())
                        .addProperty(AmqpForwardAttribute.EXCHANGE_PARAMETER_NAME, EXCHANGE)
                        .addProperty(AmqpForwardAttribute.ATTRIBUTE_PARAMETER_NAME, attribute))
                    .addMailet(MailetConfiguration.BCC_STRIPPER)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(LocalDelivery.class)))
            .build();
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailets)
            .build(folder);
        DataProbeImpl probe = jamesServer.getProbe(DataProbeImpl.class);
        probe.addDomain(DEFAULT_DOMAIN);
        probe.addUser(SENDER, PASSWORD);
        probe.addUser(TO, PASSWORD);
        probe.addUser(TO2, PASSWORD);
        probe.addUser(CC, PASSWORD);
        probe.addUser(CC2, PASSWORD);
        probe.addUser(BCC, PASSWORD);
        probe.addUser(BCC2, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void recipientsShouldBePublishedToAmqpWhenSendingEmail() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient(TO, "John To2 <" + TO2 + ">")
            .addCcRecipient(CC, "John Cc2 <" + CC2 + ">")
            .addBccRecipient(BCC, "John Bcc2 <" + BCC2 + ">")
            .setSubject("Contact collection Rocks")
            .setText("This is my email");
        FakeMail mail = FakeMail.builder()
            .mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipients(new MailAddress(TO), new MailAddress(TO2), new MailAddress(CC), new MailAddress(CC2), new MailAddress(BCC), new MailAddress(BCC2))
            .build();
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .authenticate(SENDER, PASSWORD)
            .sendMessage(mail)
            .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(TO, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));

        Optional<String> actual = amqpRule.readContent();
        assertThat(actual).isNotEmpty();
        assertThatJson(actual.get()).isEqualTo("{"
            + "\"userEmail\" : \"sender@james.org\", "
            + "\"emails\" : [ \"to@james.org\", \"John To2 <to2@james.org>\", \"cc@james.org\", \"John Cc2 <cc2@james.org>\", \"bcc@james.org\", \"John Bcc2 <bcc2@james.org>\" ]"
            + "}");
    }

}
