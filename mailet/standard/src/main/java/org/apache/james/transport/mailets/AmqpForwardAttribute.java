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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.tuple.Pair;
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
import com.google.common.base.Strings;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

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

    public static final String URI_PARAMETER_NAME = "uri";
    public static final String EXCHANGE_PARAMETER_NAME = "exchange";
    public static final String ROUTING_KEY_PARAMETER_NAME = "routing_key";
    public static final String ATTRIBUTE_PARAMETER_NAME = "attribute";

    public static final String ROUTING_KEY_DEFAULT_VALUE = "";
    public static final int MAX_ATTEMPTS = 8;
    public static final Duration MIN_BACKOFF = Duration.ofSeconds(1);

    private String exchange;
    private AttributeName attribute;
    private ConnectionFactory connectionFactory;
    @VisibleForTesting String routingKey;
    private Channel channel;
    private Connection connection;

    @Override
    public void init() throws MailetException {
        MailetConfig mailetConfig = getMailetConfig();
        String uri = preInit(mailetConfig);
        // Retry as with IE docker compose RabbitMQ startup could be delayed
        Pair<Connection, Channel> connectionAndChannel = Mono.fromCallable(() -> createConnection(uri))
            .retryWhen(Retry.backoff(MAX_ATTEMPTS, MIN_BACKOFF)
                .jitter(0.5)
                .scheduler(Schedulers.elastic()))
            .block();
        connection = connectionAndChannel.getKey();
        channel = connectionAndChannel.getValue();
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

    private Pair<Connection, Channel> createConnection(String uri) throws MailetException {
        try {
            Connection connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclarePassive(exchange);
            return Pair.of(connection, channel);
        } catch (Exception e) {
            throw new MailetException("Invalid " + URI_PARAMETER_NAME
                + " parameter was provided: " + uri, e);
        }
    }

    @PreDestroy
    public void cleanUp() throws Exception {
        channel.close();
        connection.close();
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
            return Optional.of(((Map<String, byte[]>) attributeContent).values().stream());
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
            sendContentOnChannel(channel, content);
        } catch (IOException e) {
            LOGGER.error("IOException while writing to AMQP: {}", e.getMessage(), e);
        } catch (AlreadyClosedException e) {
            LOGGER.error("AlreadyClosedException while writing to AMQP: {}", e.getMessage(), e);
        }
    }

    private void sendContentOnChannel(Channel channel, Stream<byte[]> content) throws IOException {
        content.forEach(
            Throwing.consumer(message ->
                channel.basicPublish(exchange,
                        routingKey,
                        new AMQP.BasicProperties(),
                        message)));
    }

    @Override
    public String getMailetInfo() {
        return "AmqpForwardAttribute";
    }

}
