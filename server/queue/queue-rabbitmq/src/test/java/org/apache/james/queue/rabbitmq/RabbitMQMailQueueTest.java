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

package org.apache.james.queue.rabbitmq;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerRabbitMQExtension.class)
public class RabbitMQMailQueueTest implements MailQueueContract {

    private RabbitMQMailQueueFactory mailQueueFactory;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException, URISyntaxException {

        URI rabbitManagementUri = new URIBuilder()
            .setScheme("http")
            .setHost(rabbitMQ.getHostIp())
            .setPort(rabbitMQ.getAdminPort())
            .build();
        mailQueueFactory = new RabbitMQMailQueueFactory(
            rabbitMQ.connectionFactory().newConnection(),
            new RabbitMQManagementApi(rabbitManagementUri, new RabbitMQManagementCredentials("guest", "guest".toCharArray())));
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueueFactory.createQueue("spool");
    }

    @Disabled
    @Override
    public void queueShouldPreserveMimeMessage() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveMailAttribute() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveErrorMessage() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveState() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveRemoteAddress() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveRemoteHost() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveLastUpdated() {

    }

    @Disabled
    @Override
    public void queueShouldPreservePerRecipientHeaders() {

    }

    @Disabled
    @Override
    public void queueShouldPreserveNonStringMailAttribute() {

    }
}
