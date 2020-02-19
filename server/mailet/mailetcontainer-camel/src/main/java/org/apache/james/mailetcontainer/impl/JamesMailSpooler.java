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

package org.apache.james.mailetcontainer.impl;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Manages the mail spool. This class is responsible for retrieving messages
 * from the spool, directing messages to the appropriate processor, and removing
 * them from the spool when processing is complete.
 */
public class JamesMailSpooler implements Disposable, Configurable, MailSpoolerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesMailSpooler.class);

    public static final String SPOOL_PROCESSING = "spoolProcessing";

    /**
     * The number of threads used to move mail through the spool.
     */
    private int numThreads;

    private final AtomicInteger processingActive = new AtomicInteger(0);

    private final MetricFactory metricFactory;

    /**
     * The mail processor
     */
    private final MailProcessor mailProcessor;

    private final MailQueueFactory<?> queueFactory;
    private reactor.core.Disposable disposable;
    private Scheduler spooler;
    private int parallelismLevel;
    private MailQueue queue;


    @Inject
    public JamesMailSpooler(MetricFactory metricFactory, MailProcessor mailProcessor, MailQueueFactory<?> queueFactory) {
        this.metricFactory = metricFactory;
        this.mailProcessor = mailProcessor;
        this.queueFactory = queueFactory;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        numThreads = config.getInt("threads", 100);
        //Reactor helps us run things in parallel but we have to ensure there are always threads available
        //in the threadpool to avoid starvation.
        parallelismLevel = Math.max(1, numThreads - 2);
    }

    /**
     * Initializes the spool manager.
     */
    @PostConstruct
    public void init() {
        LOGGER.info("init...");
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
        spooler = Schedulers.fromExecutor(Executors.newFixedThreadPool(numThreads, NamedThreadFactory.withName("spooler")));
        LOGGER.info("uses {} Thread(s)", numThreads);
        run();
    }

    private void run() {
        LOGGER.info("Queue={}", queue);
        disposable = Flux.from(queue.deQueue())
            .flatMap(item -> handleOnQueueItem(item).subscribeOn(spooler), parallelismLevel)
            .onErrorContinue((throwable, item) -> LOGGER.error("Exception processing mail while spooling {}", item, throwable))
            .subscribeOn(spooler)
            .subscribe();
    }

    private Mono<Void> handleOnQueueItem(MailQueueItem queueItem) {
        TimeMetric timeMetric = metricFactory.timer(SPOOL_PROCESSING);
        try {
            return Mono.fromCallable(processingActive::incrementAndGet)
                .flatMap(ignore -> processMail(queueItem).subscribeOn(spooler))
                .doOnSuccess(any -> timeMetric.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD))
                .doOnSuccess(any -> processingActive.decrementAndGet());
        } catch (Throwable e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> processMail(MailQueueItem queueItem) {
        return Mono
            .using(
                queueItem::getMail,
                resource -> Mono.just(resource)
                    .doOnNext(mail -> LOGGER.debug("==== Begin processing mail {} ====", mail.getName()))
                    .map(mail -> performProcessMail(queueItem, mail))
                    .doOnNext(mail -> LOGGER.debug("==== End processing mail {} ====", mail.getName())),
                LifecycleUtil::dispose)
            .then();
    }


    private Mail performProcessMail(MailQueueItem queueItem, Mail mail) {
        try {
            mailProcessor.service(mail);

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Thread has been interrupted");
            }
            queueItem.done(true);
        } catch (Exception e) {
            try {
                queueItem.done(false);
            } catch (MailQueue.MailQueueException ex) {
                throw new RuntimeException(e);
            }
        }
        return mail;
    }

    /**
     * The dispose operation is called at the end of a components lifecycle.
     * Instances of this class use this method to release and destroy any
     * resources that they own.
     * <p/>
     * This implementation shuts down the LinearProcessors managed by this
     * JamesSpoolManager
     */
    @PreDestroy
    @Override
    public void dispose() {
        LOGGER.info("start dispose() ...");
        disposable.dispose();
        spooler.dispose();
        try {
            queue.close();
        } catch (IOException e) {
            LOGGER.debug("error closing queue", e);
        }
        LOGGER.info("thread shutdown completed.");
    }

    @Override
    public int getThreadCount() {
        return numThreads;
    }

    @Override
    public int getCurrentSpoolCount() {
        return processingActive.get();
    }
}
