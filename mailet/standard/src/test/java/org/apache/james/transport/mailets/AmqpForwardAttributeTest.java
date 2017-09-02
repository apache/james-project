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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class AmqpForwardAttributeTest {

    private static final String MAIL_ATTRIBUTE = "ampq.attachments";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String ROUTING_KEY = "routingKey";
    private static final String AMQP_URI = "amqp://host";
    private static final byte[] ATTACHMENT_CONTENT = "Attachment content".getBytes(Charsets.UTF_8);
    private static final ImmutableMap<String, byte[]> ATTRIBUTE_CONTENT = ImmutableMap.of("attachment1.txt", ATTACHMENT_CONTENT);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AmqpForwardAttribute mailet;
    private Logger logger;
    private MailetContext mailetContext;
    private FakeMailetConfig mailetConfig;

    @Before
    public void setUp() throws Exception {
        mailet = new AmqpForwardAttribute();
        logger = mock(Logger.class);
        mailetContext = FakeMailContext.builder()
                .logger(logger)
                .build();
        mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("routing_key", ROUTING_KEY)
                .setProperty("attribute", MAIL_ATTRIBUTE)
                .build();
    }

    @Test
    public void initShouldThrowWhenNoUriParameter() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .build();
        expectedException.expect(MailetException.class);
        mailet.init(customMailetConfig);
    }

    @Test
    public void initShouldThrowWhenNoExchangeParameter() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .build();
        expectedException.expect(MailetException.class);
        mailet.init(customMailetConfig);
    }

    @Test
    public void initShouldThrowWhenNoAttributeParameter() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .build();
        expectedException.expect(MailetException.class);
        mailet.init(customMailetConfig);
    }

    @Test
    public void initShouldThrowWhenInvalidUri() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", "bad-uri")
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("attribute", MAIL_ATTRIBUTE)
                .build();
        expectedException.expect(MailetException.class);
        mailet.init(customMailetConfig);
    }

    @Test
    public void getMailetInfoShouldReturnInfo() {
        assertThat(mailet.getMailetInfo()).isEqualTo("AmqpForwardAttribute");
    }

    @Test
    public void initShouldIntializeEmptyRoutingKeyWhenAllParametersButRoutingKey() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("attribute", MAIL_ATTRIBUTE)
                .build();
        mailet.init(customMailetConfig);

        assertThat(mailet.routingKey).isEmpty();
    }

    @Test
    public void initShouldNotThrowWithAllParameters() throws MessagingException {
        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldNotUseConnectionWhenNoAttributeInMail() throws Exception {
        mailet.init(mailetConfig);
        Connection connection = mock(Connection.class);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        mailet.setConnectionFactory(connectionFactory);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(null);

        mailet.service(mail);

        verifyZeroInteractions(connection);
    }

    @Test
    public void serviceShouldThrowWhenAttributeContentIsNotAMap() throws MessagingException {
        mailet.init(mailetConfig);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(ImmutableList.of());

        expectedException.expect(MailetException.class);

        mailet.service(mail);
    }

    @Test
    public void serviceShouldNotFailWhenTimeoutException() throws Exception {
        mailet.init(mailetConfig);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(ATTRIBUTE_CONTENT);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection()).thenThrow(new TimeoutException());
        mailet.setConnectionFactory(connectionFactory);

        mailet.service(mail);
    }

    @Test
    public void serviceShouldNotFailWhenIOException() throws Exception {
        mailet.init(mailetConfig);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(ATTRIBUTE_CONTENT);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection()).thenThrow(new IOException());
        mailet.setConnectionFactory(connectionFactory);

        mailet.service(mail);
    }

    @Test
    public void serviceShouldPublishAttributeContentWhenAttributeInMail() throws Exception {
        mailet.init(mailetConfig);
        Channel channel = mock(Channel.class);
        Connection connection = mock(Connection.class);
        when(connection.createChannel()).thenReturn(channel);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        mailet.setConnectionFactory(connectionFactory);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(ATTRIBUTE_CONTENT);
        BasicProperties expectedProperties = new AMQP.BasicProperties();

        mailet.service(mail);

        ArgumentCaptor<BasicProperties> basicPropertiesCaptor = ArgumentCaptor.forClass(BasicProperties.class);
        verify(channel).basicPublish(eq(EXCHANGE_NAME), eq(ROUTING_KEY), basicPropertiesCaptor.capture(), eq(ATTACHMENT_CONTENT));
        assertThat(basicPropertiesCaptor.getValue()).isEqualToComparingFieldByField(expectedProperties);
    }
}
