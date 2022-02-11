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

import static org.apache.mailet.Mail.GHOST;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.util.DurationParser;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

public class Requeue extends GenericMailet {
    private final MailQueueFactory<?> mailQueueFactory;

    private MailQueue mailQueue;
    private Optional<Duration> delayDuration;
    private String processor;
    private boolean consume;

    @Inject
    public Requeue(MailQueueFactory<?> mailQueueFactory) {
        this.mailQueueFactory = mailQueueFactory;
    }

    @Override
    public void init() throws MessagingException {
        MailQueueName mailQueueName = Optional.ofNullable(getInitParameter("queue"))
            .map(MailQueueName::of).orElse(MailQueueFactory.SPOOL);

        mailQueue = mailQueueFactory.createQueue(mailQueueName);
        delayDuration = Optional.ofNullable(getInitParameter("delay"))
            .map(delayValue -> DurationParser.parse(delayValue, ChronoUnit.SECONDS));
        processor = Optional.ofNullable(getInitParameter("processor"))
            .orElse(Mail.DEFAULT);

        consume = getInitParameter("consume", true);

        Preconditions.checkArgument(delayDuration.isEmpty() || !delayDuration.get().isNegative(),
            "Duration should be non-negative");
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (consume) {
            enqueue(mail);
            mail.setState(GHOST);
        } else {
            Mail newMail = null;
            try {
                newMail = mail.duplicate();
                enqueue(newMail);
            } finally {
                LifecycleUtil.dispose(newMail);
            }
        }
    }

    private void enqueue(Mail mail) {
        mail.setState(processor);
        delayDuration.ifPresentOrElse(
            Throwing.consumer(delay -> mailQueue.enQueue(mail, delay)),
            Throwing.runnable(() -> mailQueue.enQueue(mail)).sneakyThrow());
    }
}
