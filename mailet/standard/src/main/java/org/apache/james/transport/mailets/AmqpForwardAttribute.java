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
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

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

    private String exchange;
    private String attribute;
    private ConnectionFactory connectionFactory;
    @VisibleForTesting String routingKey;

    @Override
    public void init() throws MailetException {
        String uri = getInitParameter(URI_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(uri)) {
            throw new MailetException("No value for " + URI_PARAMETER_NAME
                    + " parameter was provided.");
        }
        exchange = getInitParameter(EXCHANGE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(exchange)) {
            throw new MailetException("No value for " + EXCHANGE_PARAMETER_NAME
                    + " parameter was provided.");
        }
        routingKey = getInitParameter(ROUTING_KEY_PARAMETER_NAME, ROUTING_KEY_DEFAULT_VALUE);
        attribute = getInitParameter(ATTRIBUTE_PARAMETER_NAME);
        if (Strings.isNullOrEmpty(attribute)) {
            throw new MailetException("No value for " + ATTRIBUTE_PARAMETER_NAME
                    + " parameter was provided.");
        }
        connectionFactory = new ConnectionFactory();
        try {
            connectionFactory.setUri(uri);
        } catch (Exception e) {
            throw new MailetException("Invalid " + URI_PARAMETER_NAME
                    + " parameter was provided: " + uri, e);
        }
    }

    @VisibleForTesting void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void service(Mail mail) throws MailetException {
        if (mail.getAttribute(attribute) == null) {
            return;
        }
        Map<String, byte[]> content = getAttributeContent(mail);
        try {
            sendContent(content);
        } catch (IOException e) {
            LOGGER.error("IOException while writing to AMQP: " + e.getMessage(), e);
        } catch (TimeoutException e) {
            LOGGER.error("TimeoutException while writing to AMQP: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, byte[]> getAttributeContent(Mail mail) throws MailetException {
        Serializable attributeContent = mail.getAttribute(attribute);
        if (! (attributeContent instanceof Map)) {
            throw new MailetException("Invalid attribute found into attribute "
                    + attribute + "class Map expected but "
                    + attributeContent.getClass() + " found.");
        }
        return (Map<String, byte[]>) attributeContent;
    }

    private void sendContent(Map<String, byte[]> content) throws IOException, TimeoutException {
        Connection connection = null;
        Channel channel = null;
        try {
            connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclarePassive(exchange);
            sendContentOnChannel(channel, content);
        } finally {
            if (channel != null) {
                channel.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    private void sendContentOnChannel(Channel channel, Map<String, byte[]> content) throws IOException {
        for (byte[] body: content.values()) {
            channel.basicPublish(exchange, 
                    routingKey, 
                    new AMQP.BasicProperties(), 
                    body);
        }
    }

    @Override
    public String getMailetInfo() {
        return "AmqpForwardAttribute";
    }

}
