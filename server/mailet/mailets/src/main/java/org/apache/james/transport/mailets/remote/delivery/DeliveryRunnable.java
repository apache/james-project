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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.IS_DELIVERY_PERMANENT_ERROR;

import java.io.Closeable;
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
import org.apache.james.util.MDCBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

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
    private final MailetContext mailetContext;
    private Disposable disposable;
    private Scheduler remoteDeliveryProcessScheduler;
    private Scheduler remoteDeliveryDequeueScheduler;

    public DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, DNSService dnsServer, MetricFactory metricFactory,
                            MailetContext mailetContext, Bouncer bouncer) {
        this(queue, configuration, metricFactory, bouncer,
            new MailDelivrer(configuration, new MailDelivrerToHost(configuration, mailetContext), dnsServer, bouncer, mailetContext),
            CURRENT_DATE_SUPPLIER, mailetContext);
    }

    @VisibleForTesting
    DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, MetricFactory metricFactory, Bouncer bouncer,
                     MailDelivrer mailDelivrer, Supplier<Date> dateSupplier, MailetContext mailetContext) {
        this.queue = queue;
        this.configuration = configuration;
        this.outgoingMailsMetric = metricFactory.generate(OUTGOING_MAILS);
        this.bouncer = bouncer;
        this.mailDelivrer = mailDelivrer;
        this.dateSupplier = dateSupplier;
        this.metricFactory = metricFactory;
        this.mailetContext = mailetContext;
    }

    public void start() {
        remoteDeliveryProcessScheduler = Schedulers.newBoundedElastic(Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "RemoteDelivery-Process");
        remoteDeliveryDequeueScheduler = Schedulers.newSingle("RemoteDelivery-Dequeue");
        disposable = Flux.from(queue.deQueue())
            .flatMap(queueItem -> runStep(queueItem).subscribeOn(remoteDeliveryProcessScheduler), Queues.SMALL_BUFFER_SIZE)
            .onErrorContinue(((throwable, nothing) -> LOGGER.error("Exception caught in RemoteDelivery", throwable)))
            .subscribeOn(remoteDeliveryDequeueScheduler)
            .subscribe();
    }

    private Mono<Void> runStep(MailQueue.MailQueueItem queueItem) {
        TimeMetric timeMetric = metricFactory.timer(REMOTE_DELIVERY_TRIAL);

        return processMail(queueItem)
            .doOnSuccess(any -> timeMetric.stopAndPublish());
    }

    private Mono<Void> processMail(MailQueue.MailQueueItem queueItem) {
        return Mono.create(sink -> {
            Mail mail = queueItem.getMail();

            try (Closeable closeable =
                     MDCBuilder.create()
                        .addToContext("mail", mail.getName())
                        .addToContext("recipients", ImmutableList.copyOf(mail.getRecipients()).toString())
                        .addToContext("sender", mail.getMaybeSender().asString())
                        .build()) {
                LOGGER.debug("will process mail {}", mail.getName());
                attemptDelivery(mail);
                queueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                sink.success();
            } catch (Exception e) {
                try {
                    // Prevent unexpected exceptions from causing looping by removing message from outgoing.
                    // DO NOT CHANGE THIS to catch Error!
                    // For example, if there were an OutOfMemory condition caused because
                    // something else in the server was abusing memory, we would not want to start purging the retrying spool!
                    queueItem.done(MailQueue.MailQueueItem.CompletionStatus.RETRY);
                } catch (Exception ex) {
                    sink.error(ex);
                    return;
                }
                sink.error(e);
            } finally {
                LifecycleUtil.dispose(mail);
            }
        });
    }

    @VisibleForTesting
    void attemptDelivery(Mail mail) throws MailQueue.MailQueueException {
        ExecutionResult executionResult = mailDelivrer.deliver(mail);
        switch (executionResult.getExecutionState()) {
            case SUCCESS:
                outgoingMailsMetric.increment();
                configuration.getOnSuccess()
                    .ifPresent(Throwing.consumer(onSuccess -> mailetContext.sendMail(mail, onSuccess.getValue())));
                break;
            case TEMPORARY_FAILURE:
                handleTemporaryFailure(mail, executionResult);
                break;
            case PERMANENT_FAILURE:
                handlePermanentFailure(mail, executionResult);
                break;
        }
    }

    private void handlePermanentFailure(Mail mail, ExecutionResult executionResult) {
        mail.setAttribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(true)));
        bouncer.bounce(mail, executionResult.getException().orElse(null));
    }

    private void handleTemporaryFailure(Mail mail, ExecutionResult executionResult) throws MailQueue.MailQueueException {
        if (!mail.getState().equals(Mail.ERROR)) {
            mail.setState(Mail.ERROR);
            DeliveryRetriesHelper.initRetries(mail);
            mail.setLastUpdated(dateSupplier.get());
        }
        mail.setAttribute(new Attribute(IS_DELIVERY_PERMANENT_ERROR, AttributeValue.of(false)));
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

    private Duration getNextDelay(int retryCount) {
        if (retryCount > configuration.getDelayTimes().size()) {
            return Delay.DEFAULT_DELAY_TIME;
        }
        return configuration.getDelayTimes().get(retryCount - 1);
    }

    @Override
    public void dispose() {
        disposable.dispose();
        remoteDeliveryDequeueScheduler.dispose();
        remoteDeliveryProcessScheduler.disposeGracefully()
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(e -> Mono.empty())
            .block();
    }
}
