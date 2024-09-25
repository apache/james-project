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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_ACK;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.amqp.AmqpExtension;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

class AmqpForwardAttributeIntegrationTest {
    public static final String SENDER = "sender@" + DEFAULT_DOMAIN;
    public static final String TO = "to@" + DEFAULT_DOMAIN;
    public static final String EXTRACT_ATTRIBUTE = "ExtractedContacts";

    @RegisterExtension
    public static AmqpExtension amqpExtension = new AmqpExtension("any", "any");

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private Connection rabbitMQConnection;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUri(amqpExtension.getAmqpUri());

        rabbitMQConnection = connectionFactory.newConnection();
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
        if (rabbitMQConnection != null) {
            try {
                rabbitMQConnection.close();
            } catch (Exception ignored) {
                // Ignored
            }
        }
    }

    private void buildJameServer(File temporaryFolder, MailetConfiguration amqpForwardAttributeMailet) throws Exception {
        MailetContainer.Builder mailets = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .postmaster(SENDER)
            .putProcessor(
                ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(SMTPAuthSuccessful.class)
                        .mailet(ContactExtractor.class)
                        .addProperty(ContactExtractor.Configuration.ATTRIBUTE, EXTRACT_ATTRIBUTE))
                    .addMailet(amqpForwardAttributeMailet)
                    .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailets)
            .build(temporaryFolder);

        jamesServer.start();
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(SENDER, PASSWORD)
            .addUser(TO, PASSWORD);
    }

    private Optional<String> getAmqpMessage(Channel rabbitMQChannel, String queueName) throws Exception {
        return Optional.ofNullable(rabbitMQChannel.basicGet(queueName, AUTO_ACK))
            .map(GetResponse::getBody)
            .map(value -> new String(value, StandardCharsets.UTF_8));
    }

    @Test
    void recipientsShouldBePublishedToAmqpWhenSendingEmailWithExchangeTypeFanoutConfiguration(@TempDir File temporaryFolder) throws Exception {
        // Given AmqpForwardAttribute mailet configured with exchange type fanout
        String exchangeName = "collector:email" + UUID.randomUUID();
        String routingKey = "routing1" + UUID.randomUUID();

        MailetConfiguration amqpForwardAttributeMailet = MailetConfiguration.builder()
            .matcher(All.class)
            .mailet(AmqpForwardAttribute.class)
            .addProperty(AmqpForwardAttribute.URI_PARAMETER_NAME, amqpExtension.getAmqpUri())
            .addProperty(AmqpForwardAttribute.EXCHANGE_PARAMETER_NAME, exchangeName)
            .addProperty(AmqpForwardAttribute.EXCHANGE_TYPE_PARAMETER_NAME, BuiltinExchangeType.FANOUT.getType())
            .addProperty(AmqpForwardAttribute.ATTRIBUTE_PARAMETER_NAME, EXTRACT_ATTRIBUTE)
            .build();

        buildJameServer(temporaryFolder, amqpForwardAttributeMailet);

        Channel channel = rabbitMQConnection.createChannel();
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchangeName, routingKey);

        // when sending an email
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(SENDER)
                    .addToRecipient(TO)
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(SENDER)
                .recipients(TO));

        // then the mailet should be published to AMQP
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(TO, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> actual = getAmqpMessage(channel, queueName);
        assertThat(actual).isNotEmpty();
        assertThatJson(actual.get()).isEqualTo("{"
            + "\"userEmail\" : \"sender@james.org\", "
            + "\"emails\" : [ \"to@james.org\"]"
            + "}");
    }

    @Test
    void mailetShouldWorkNormalWhenExchangeAlreadyExistsWithDifferentConfiguration(@TempDir File temporaryFolder) throws Exception {
        // Given an exchange already exists with different configuration
        String exchangeName = "collector:email" + UUID.randomUUID();
        String routingKey = "routing1" + UUID.randomUUID();

        Channel channel = rabbitMQConnection.createChannel();
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true, true, null);
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchangeName, routingKey);

        MailetConfiguration amqpForwardAttributeMailet = MailetConfiguration.builder()
            .matcher(All.class)
            .mailet(AmqpForwardAttribute.class)
            .addProperty(AmqpForwardAttribute.URI_PARAMETER_NAME, amqpExtension.getAmqpUri())
            .addProperty(AmqpForwardAttribute.EXCHANGE_PARAMETER_NAME, exchangeName)
            .addProperty(AmqpForwardAttribute.EXCHANGE_TYPE_PARAMETER_NAME, BuiltinExchangeType.DIRECT.getType())
            .addProperty(AmqpForwardAttribute.ATTRIBUTE_PARAMETER_NAME, EXTRACT_ATTRIBUTE)
            .build();

        assertThatCode(() -> buildJameServer(temporaryFolder, amqpForwardAttributeMailet))
            .doesNotThrowAnyException();

        // When sending an email
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(SENDER)
                    .addToRecipient(TO)
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(SENDER)
                .recipients(TO));

        // Then the mailet should publish AQMP message
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(TO, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        Optional<String> actual = getAmqpMessage(channel, queueName);
        assertThat(actual).isNotEmpty();
        assertThatJson(actual.get()).isEqualTo("{"
            + "\"userEmail\" : \"sender@james.org\", "
            + "\"emails\" : [ \"to@james.org\"]"
            + "}");
    }
}
