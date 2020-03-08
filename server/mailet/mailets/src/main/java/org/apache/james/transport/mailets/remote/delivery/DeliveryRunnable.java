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

package org.apache.james.transport.mailets.remote.delivery;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.time.Duration;
import java.util.Date;
import java.util.function.Supplier;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class DeliveryRunnable implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryRunnable.class);

    public static final Supplier<Date> CURRENT_DATE_SUPPLIER = Date::new;
    public static final String OUTGOING_MAILS = "outgoingMails";
    public static final String REMOTE_DELIVERY_TRIAL = "RemoteDeliveryTrial";

    private final MailQueue queue;
    private final RemoteDeliveryConfiguration configuration;
    private final Metric outgoingMailsMetric;
    private final MetricFactory metricFactory;
    private final Bouncer bouncer;
    private final MailDelivrer mailDelivrer;
    private final Supplier<Date> dateSupplier;
    private Disposable disposable;
    private Scheduler remoteDeliveryScheduler;

    public DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, DNSService dnsServer, MetricFactory metricFactory,
                            MailetContext mailetContext, Bouncer bouncer) {
        this(queue, configuration, metricFactory, bouncer,
            new MailDelivrer(configuration, new MailDelivrerToHost(configuration, mailetContext), dnsServer, bouncer),
            CURRENT_DATE_SUPPLIER);
    }

    @VisibleForTesting
    DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, MetricFactory metricFactory, Bouncer bouncer,
                     MailDelivrer mailDelivrer, Supplier<Date> dateSupplier) {
        this.queue = queue;
        this.configuration = configuration;
        this.outgoingMailsMetric = metricFactory.generate(OUTGOING_MAILS);
        this.bouncer = bouncer;
        this.mailDelivrer = mailDelivrer;
        this.dateSupplier = dateSupplier;
        this.metricFactory = metricFactory;
    }

    public void start() {
        remoteDeliveryScheduler = Schedulers.newBoundedElastic(Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "RemoteDelivery");
        disposable = Flux.from(queue.deQueue())
            .flatMap(queueItem -> runStep(queueItem).subscribeOn(remoteDeliveryScheduler))
            .onErrorContinue(((throwable, nothing) -> LOGGER.error("Exception caught in RemoteDelivery", throwable)))
            .subscribeOn(remoteDeliveryScheduler)
            .subscribe();
    }

    private Mono<Void> runStep(MailQueue.MailQueueItem queueItem) {
        TimeMetric timeMetric = metricFactory.timer(REMOTE_DELIVERY_TRIAL);
        try {
            return processMail(queueItem)
                .doOnSuccess(any -> timeMetric.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD));
        } catch (Throwable e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> processMail(MailQueue.MailQueueItem queueItem) throws MailQueue.MailQueueException {
        Mail mail = queueItem.getMail();

        try {
            LOGGER.debug("will process mail {}", mail.getName());
            attemptDelivery(mail);
            queueItem.done(true);
            return Mono.empty();
        } catch (Exception e) {
            // Prevent unexpected exceptions from causing looping by removing message from outgoing.
            // DO NOT CHANGE THIS to catch Error!
            // For example, if there were an OutOfMemory condition caused because
            // something else in the server was abusing memory, we would not want to start purging the retrying spool!
            queueItem.done(false);
            return Mono.error(e);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    @VisibleForTesting
    void attemptDelivery(Mail mail) throws MailQueue.MailQueueException {
        ExecutionResult executionResult = mailDelivrer.deliver(mail);
        switch (executionResult.getExecutionState()) {
            case SUCCESS:
                outgoingMailsMetric.increment();
                break;
            case TEMPORARY_FAILURE:
                handleTemporaryFailure(mail, executionResult);
                break;
            case PERMANENT_FAILURE:
                bouncer.bounce(mail, executionResult.getException().orElse(null));
                break;
        }
    }

    private void handleTemporaryFailure(Mail mail, ExecutionResult executionResult) throws MailQueue.MailQueueException {
        if (!mail.getState().equals(Mail.ERROR)) {
            mail.setState(Mail.ERROR);
            DeliveryRetriesHelper.initRetries(mail);
            mail.setLastUpdated(dateSupplier.get());
        }
        int retries = DeliveryRetriesHelper.retrieveRetries(mail);

        if (retries < configuration.getMaxRetries()) {
            reAttemptDelivery(mail, retries);
        } else {
            LOGGER.debug("Bouncing message {} after {} retries", mail.getName(), retries);
            bouncer.bounce(mail, new Exception("Too many retries failure. Bouncing after " + retries + " retries.", executionResult.getException().orElse(null)));
        }
    }

    private void reAttemptDelivery(Mail mail, int retries) throws MailQueue.MailQueueException {
        LOGGER.debug("Storing message {} into outgoing after {} retries", mail.getName(), retries);
        DeliveryRetriesHelper.incrementRetries(mail);
        mail.setLastUpdated(dateSupplier.get());
        // Something happened that will delay delivery. Store it back in the retry repository.
        Duration delay = getNextDelay(DeliveryRetriesHelper.retrieveRetries(mail));

        if (configuration.isUsePriority()) {
            // Use lowest priority for retries. See JAMES-1311
            mail.setAttribute(MailPrioritySupport.LOW_PRIORITY_ATTRIBUTE);
        }
        queue.enQueue(mail, delay);
    }

    private Duration getNextDelay(int retry_count) {
        if (retry_count > configuration.getDelayTimes().size()) {
            return Delay.DEFAULT_DELAY_TIME;
        }
        return configuration.getDelayTimes().get(retry_count - 1);
    }

    @Override
    public void dispose() {
        disposable.dispose();
        remoteDeliveryScheduler.dispose();
    }
}
