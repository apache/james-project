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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.ConnectionFactory;

import reactor.core.publisher.Flux;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

/**
 * This mailet forwards the attributes values to a AMPQ.
 * <br />
 * It takes 4 parameters:
 * <ul>
 * <li>attribute (mandatory): content to be forwarded, expected to be a Map&lt;String, byte[]&gt;
 * where the byte[] content is issued from a MimeBodyPart.
 * It is typically generated from the StripAttachment mailet.</li>
 * <li>uri (mandatory): AMQP URI defining the server where to send the attachment.</li>
 * <li>exchange (mandatory): name of the AMQP exchange.</li>
 * <li>routing_key (optional, default to empty string): name of the routing key on this exchange.</li>
 * </ul>
 *
 * This mailet will extract the attachment content from the MimeBodyPart byte[] before
 * sending it.
 */
public class AmqpForwardAttribute extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpForwardAttribute.class);
    private static final int MAX_THREE_RETRIES = 3;
    private static final int MIN_DELAY_OF_TEN_MILLISECONDS = 10;
    private static final int CONNECTION_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int CHANNEL_RPC_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int HANDSHAKE_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int SHUTDOWN_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int NETWORK_RECOVERY_INTERVAL_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final String DEFAULT_USER = "guest";
    private static final String DEFAULT_PASSWORD_STRING = "guest";
    private static final char[] DEFAULT_PASSWORD = DEFAULT_PASSWORD_STRING.toCharArray();
    static final RabbitMQConfiguration.ManagementCredentials DEFAULT_MANAGEMENT_CREDENTIAL = new RabbitMQConfiguration.ManagementCredentials(DEFAULT_USER, DEFAULT_PASSWORD);


    public static final String URI_PARAMETER_NAME = "uri";
    public static final String EXCHANGE_PARAMETER_NAME = "exchange";
    public static final String ROUTING_KEY_PARAMETER_NAME = "routing_key";
    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";

    public static final String ROUTING_KEY_DEFAULT_VALUE = "";

    private final MetricFactory metricFactory;

    private String exchange;
    private AttributeName attribute;
    private ConnectionFactory connectionFactory;
    @VisibleForTesting String routingKey;
    private SimpleConnectionPool connectionPool;
    private ReactorRabbitMQChannelPool reactorRabbitMQChannelPool;
    private Sender sender;

    @Inject
    public AmqpForwardAttribute(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void init() throws MailetException {
        MailetConfig mailetConfig = getMailetConfig();
        String uri = preInit(mailetConfig);

        try {
            URI amqpUri = new URI(uri);
            RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
                .amqpUri(amqpUri)
                .managementUri(amqpUri)
                .managementCredentials(retrieveCredentials(amqpUri))
                .maxRetries(MAX_THREE_RETRIES)
                .minDelayInMs(MIN_DELAY_OF_TEN_MILLISECONDS)
                .connectionTimeoutInMs(CONNECTION_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
                .channelRpcTimeoutInMs(CHANNEL_RPC_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
                .handshakeTimeoutInMs(HANDSHAKE_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
                .shutdownTimeoutInMs(SHUTDOWN_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
                .networkRecoveryIntervalInMs(NETWORK_RECOVERY_INTERVAL_OF_ONE_HUNDRED_MILLISECOND)
                .build();
            connectionPool = new SimpleConnectionPool(new RabbitMQConnectionFactory(rabbitMQConfiguration), SimpleConnectionPool.Configuration.builder()
                .retries(2)
                .initialDelay(Duration.ofMillis(5)));
            reactorRabbitMQChannelPool = new ReactorRabbitMQChannelPool(connectionPool.getResilientConnection(),
                ReactorRabbitMQChannelPool.Configuration.DEFAULT,
                metricFactory);
            reactorRabbitMQChannelPool.start();
            sender = reactorRabbitMQChannelPool.getSender();
            sender.declareExchange(ExchangeSpecification.exchange(exchange));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    RabbitMQConfiguration.ManagementCredentials retrieveCredentials(URI amqpUri) {
        return Optional.ofNullable(amqpUri.getUserInfo())
            .map(this::parseUserInfo)
            .orElse(DEFAULT_MANAGEMENT_CREDENTIAL);
    }

    private RabbitMQConfiguration.ManagementCredentials parseUserInfo(String userInfo) {
        Preconditions.checkArgument(userInfo.contains(":"), "User info needs a password part");

        List<String> parts = Splitter.on(':')
            .splitToList(userInfo);
        ImmutableList<String> passwordParts = parts.stream()
            .skip(1)
            .collect(ImmutableList.toImmutableList());

        return new RabbitMQConfiguration.ManagementCredentials(
            parts.get(0),
            Joiner.on(':')
                .join(passwordParts)
                .toCharArray());
    }

    @VisibleForTesting
    String preInit(MailetConfig mailetConfig) throws MailetException {
        String uri = mailetConfig.getInitParameter(URI_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(uri)) {
            throw new MailetException("No value for " + URI_PARAMETER_NAME
                    + " parameter was provided.");
        }
        exchange = mailetConfig.getInitParameter(EXCHANGE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(exchange)) {
            throw new MailetException("No value for " + EXCHANGE_PARAMETER_NAME
                    + " parameter was provided.");
        }
        routingKey = Optional.ofNullable(mailetConfig.getInitParameter(ROUTING_KEY_PARAMETER_NAME))
            .orElse(ROUTING_KEY_DEFAULT_VALUE);
        String rawAttribute = mailetConfig.getInitParameter(ATTRIBUTE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(rawAttribute)) {
            throw new MailetException("No value for " + ATTRIBUTE_PARAMETER_NAME
                    + " parameter was provided.");
        }
        attribute = AttributeName.of(rawAttribute);
        connectionFactory = new ConnectionFactory();
        try {
            connectionFactory.setUri(uri);
        } catch (Exception e) {
            throw new MailetException("Invalid " + URI_PARAMETER_NAME
                    + " parameter was provided: " + uri, e);
        }
        return uri;
    }

    @PreDestroy
    public void cleanUp() {
        sender.close();
        reactorRabbitMQChannelPool.close();
        connectionPool.close();
    }

    @VisibleForTesting void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void service(Mail mail) throws MailetException {
        mail.getAttribute(attribute)
            .map(Throwing.function(this::getAttributeContent).sneakyThrow())
            .ifPresent(this::sendContent);
    }

    private Stream<byte[]> getAttributeContent(Attribute attribute) throws MailetException {
        return extractAttributeValueContent(attribute.getValue().value())
                .orElseThrow(() -> new MailetException("Invalid attribute found into attribute "
                    + this.attribute.asString() + "class Map or List or String expected but "
                    + attribute.toString() + " found."));
    }

    @SuppressWarnings("unchecked")
    private Optional<Stream<byte[]>> extractAttributeValueContent(Object attributeContent) {
        if (attributeContent instanceof Map) {
            return Optional.of(((Map<String, AttributeValue<byte[]>>) attributeContent).values().stream()
                .map(AttributeValue::getValue));
        }
        if (attributeContent instanceof List) {
            return Optional.of(((List<AttributeValue<byte[]>>) attributeContent).stream().map(AttributeValue::value));
        }
        if (attributeContent instanceof String) {
            return Optional.of(Stream.of(((String) attributeContent).getBytes(StandardCharsets.UTF_8)));
        }
        return Optional.empty();
    }

    private void sendContent(Stream<byte[]> content) {
        try {
            sender.send(Flux.fromStream(content)
                .map(bytes -> new OutboundMessage(exchange, routingKey, bytes)))
                .block();
        } catch (AlreadyClosedException e) {
            LOGGER.error("AlreadyClosedException while writing to AMQP: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("IOException while writing to AMQP: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "AmqpForwardAttribute";
    }

}
