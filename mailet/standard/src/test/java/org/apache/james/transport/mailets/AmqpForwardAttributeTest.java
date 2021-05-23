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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.mail.MessagingException;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Channel.Close;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownSignalException;

class AmqpForwardAttributeTest {

    private static final AttributeName MAIL_ATTRIBUTE = AttributeName.of("ampq.attachments");
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String ROUTING_KEY = "routingKey";
    private static final String AMQP_URI = "amqp://host";
    private static final byte[] ATTACHMENT_CONTENT = "Attachment content".getBytes(StandardCharsets.UTF_8);
    private static final ImmutableMap<String, byte[]> ATTRIBUTE_VALUE = ImmutableMap.of("attachment1.txt", ATTACHMENT_CONTENT);
    private static final Optional<Attribute> ATTRIBUTE_CONTENT = Optional.of(new Attribute(MAIL_ATTRIBUTE, AttributeValue.ofAny(ATTRIBUTE_VALUE)));

    private AmqpForwardAttribute mailet;
    private MailetContext mailetContext;
    private FakeMailetConfig mailetConfig;

    @BeforeEach
    public void setUp() throws Exception {
        mailet = new AmqpForwardAttribute();
        Logger logger = mock(Logger.class);
        mailetContext = FakeMailContext.builder()
                .logger(logger)
                .build();
        mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("routing_key", ROUTING_KEY)
                .setProperty("attribute", MAIL_ATTRIBUTE.asString())
                .build();
    }

    @Test
    void initShouldThrowWhenNoUriParameter() {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .build();
        assertThatThrownBy(() -> mailet.preInit(customMailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenNoExchangeParameter() {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .build();
        assertThatThrownBy(() -> mailet.preInit(customMailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenNoAttributeParameter() {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .build();
        assertThatThrownBy(() -> mailet.preInit(customMailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void initShouldThrowWhenInvalidUri() {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", "bad-uri")
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("attribute", MAIL_ATTRIBUTE.asString())
                .build();
        assertThatThrownBy(() -> mailet.preInit(customMailetConfig))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void getMailetInfoShouldReturnInfo() {
        assertThat(mailet.getMailetInfo()).isEqualTo("AmqpForwardAttribute");
    }

    @Test
    void initShouldIntializeEmptyRoutingKeyWhenAllParametersButRoutingKey() throws MessagingException {
        FakeMailetConfig customMailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .mailetContext(mailetContext)
                .setProperty("uri", AMQP_URI)
                .setProperty("exchange", EXCHANGE_NAME)
                .setProperty("attribute", MAIL_ATTRIBUTE.asString())
                .build();
        mailet.preInit(customMailetConfig);

        assertThat(mailet.routingKey).isEmpty();
    }

    @Test
    void initShouldNotThrowWithAllParameters() throws MessagingException {
        mailet.preInit(mailetConfig);
    }

    @Test
    public void serviceShouldThrowWhenAttributeContentIsNotAMapAListOrAString() throws MessagingException {
        mailet.preInit(mailetConfig);
        Mail mail = mock(Mail.class);
        when(mail.getAttribute(MAIL_ATTRIBUTE)).thenReturn(Optional.of(new Attribute(MAIL_ATTRIBUTE, AttributeValue.of(2))));

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MailetException.class);
    }
}
