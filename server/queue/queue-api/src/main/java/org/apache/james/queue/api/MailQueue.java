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

package org.apache.james.queue.api;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;
import org.threeten.extra.Temporals;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

/**
 * <p>
 * A Queue/Spool for Mails. How the Queue handles the ordering of the dequeuing
 * is up to the implementation.
 * </p>
 * <p>
 * <strong> IMPORTANT</strong>:<br>
 * Implementations does not need to keep all {@link Mail} Attributes when
 * enqueue emails. The implementations are only in the need of supporting at
 * least this kind of Primitives as values:
 * <ul>
 * <li>
 * Long</li>
 * <li>
 * Byte</li>
 * <li>
 * Integer</li>
 * <li>
 * String</li>
 * <li>
 * Boolean</li>
 * <li>
 * Short</li>
 * <li>
 * Float</li>
 * <li>
 * Double</li>
 * </ul>
 * </p>
 */

public interface MailQueue extends Closeable {

    String ENQUEUED_METRIC_NAME_PREFIX = "enqueuedMail:";
    String DEQUEUED_METRIC_NAME_PREFIX = "dequeuedMail:";
    String ENQUEUED_TIMER_METRIC_NAME_PREFIX = "enqueueTime:";
    String QUEUE_SIZE_METRIC_NAME_PREFIX = "mailQueueSize:";

    /**
     * No delay for queued {@link MailQueueItem}
     */
    int NO_DELAY = -1;

    MailQueueName getName();

    /**
     * Enqueue the Mail to the queue. The given delay and unit are used to
     * calculate the time when the Mail will be available for deQueue
     *
     * @param mail
     * @param delay
     * @throws MailQueueException
     */
    void enQueue(Mail mail, Duration delay) throws MailQueueException;


    /**
     * Enqueue the Mail to the queue. The given delay and unit are used to
     * calculate the time when the Mail will be available for deQueue
     * 
     * @param mail
     * @param delay
     * @param unit
     * @throws MailQueueException
     */
    default void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        enQueue(mail, Temporals.chronoUnit(unit).getDuration().multipliedBy(delay));
    }

    /**
     * Enqueue the Mail to the queue
     * 
     * @param mail
     * @throws MailQueueException
     */
    void enQueue(Mail mail) throws MailQueueException;

    Publisher<Void> enqueueReactive(Mail mail);

    default Publisher<Void> enqueueReactive(Mail mail, Duration delay) {
        return Mono.fromRunnable(Throwing.runnable(() -> enQueue(mail, delay)).sneakyThrow());
    }

    /**
     * Dequeue the next ready-to-process Mail of the queue. This method will
     * block until a Mail is ready and then process the operation.
     * Implementations should take care to do some kind of transactions to not
     * loose any mail on error
     */
    Publisher<MailQueueItem> deQueue();

    /**
     * Exception which will get thrown if any problems occur while working the
     * {@link MailQueue}
     */
    class MailQueueException extends MessagingException {
        public MailQueueException(String msg, Exception e) {
            super(msg, e);
        }

        public MailQueueException(String msg) {
            super(msg);
        }
    }

    /**
     *
     */
    interface MailQueueItem {
        enum CompletionStatus {
            SUCCESS,
            RETRY,
            REJECT
        }

        /**
         * Return the dequeued {@link Mail}
         * 
         * @return mail
         */
        Mail getMail();

        /**
         * Callback which MUST get called after the operation on the dequeued
         * {@link Mail} was complete.
         * 
         * This is mostly used to either commit a transaction or rollback.
         * 
         * @param success
         * @throws MailQueueException
         */
        void done(CompletionStatus success) throws MailQueueException;
    }
}
