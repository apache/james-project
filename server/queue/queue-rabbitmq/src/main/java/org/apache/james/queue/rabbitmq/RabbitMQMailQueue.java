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

import java.util.concurrent.TimeUnit;

import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;

public class RabbitMQMailQueue implements MailQueue {
    private final MailQueueName name;

    public RabbitMQMailQueue(MailQueueName name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name.asString();
    }

    @Override
    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {

    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {

    }

    @Override
    public MailQueueItem deQueue() throws MailQueueException, InterruptedException {
        return null;
    }
}