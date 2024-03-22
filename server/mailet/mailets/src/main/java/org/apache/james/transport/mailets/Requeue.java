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

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.util.DurationParser;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * <p><b>Requeue</b> puts back the email in a queue.
 * It can be used for throttling when combined with rate limiting mailet. {@link org.apache.james.transport.mailets.PerSenderRateLimit}</p>
 *
 * <ul>Here are supported configuration parameters:
 *      <li><b>queue</b>: a Mail Queue name (optional, default to spool).</li>
 *      <li><b>processor</b>: a target processor (optional, defaults to root).</li>
 *      <li><b>delay</b>: a delay when en-queueing mail (optional, defaults to none). Supported units include: s (second), m (minute), h (hour), d (day).</li>
 *      <li><b>consume</b>: a boolean. true will consume the mail: no further processing would be done on the mail (default)
 *      while false will continue the processing of the email in addition to the requeue operation, which effectively duplicates the email.</li>
 *  </ul>
 *
 *  <p>For instance, to apply all the examples given above:</p>
 *
 *  <pre><code>
 *  &lt;mailet matcher=&quot;All&quot; class=&quot;Requeue&quot;&gt;
 *      &lt;queue&gt;spool&lt;/queue&gt;
 *      &lt;processor&gt;root&lt;/processor&gt;
 *      &lt;delay&gt;2h&lt;/delay&gt;
 *      &lt;consume&gt;true&lt;/consume&gt;
 *  &lt;/mailet&gt;
 *   </code></pre>
 *
 * <p>Note that this is a naive approach: if you have a large number of emails (say N) submitted at once,
 * you would need O(N) re-queues to eventually send all your emails,
 * where the constant is the number of mail allowed over the time window.
 * So this gives an overall complexity of O(N2). </p>
 */
public class Requeue extends GenericMailet {
    private final MailQueueFactory<?> mailQueueFactory;

    private MailQueue mailQueue;
    private Optional<Duration> delayDuration;
    private ProcessingState processor;
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
            .map(ProcessingState::new)
            .orElse(new ProcessingState(Mail.DEFAULT));

        consume = getInitParameter("consume", true);

        Preconditions.checkArgument(delayDuration.isEmpty() || !delayDuration.get().isNegative(),
            "Duration should be non-negative");
    }

    public void destroy() {
        try {
            mailQueue.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(processor);
    }

    private void enqueue(Mail mail) {
        mail.setState(processor.getValue());
        delayDuration.ifPresentOrElse(
            Throwing.consumer(delay -> mailQueue.enQueue(mail, delay)),
            Throwing.runnable(() -> mailQueue.enQueue(mail)).sneakyThrow());
    }
}
