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

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.HierarchicalConfiguration;
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
    private MailQueue queue;

    /**
     * The number of threads used to move mail through the spool.
     */
    private int numThreads;

    private final AtomicInteger processingActive = new AtomicInteger(0);

    private final MetricFactory metricFactory;

    /**
     * The mail processor
     */
    private MailProcessor mailProcessor;

    private MailQueueFactory<?> queueFactory;
    private reactor.core.Disposable disposable;
    private Scheduler spooler;

    @Inject
    public JamesMailSpooler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Inject
    public void setMailQueueFactory(MailQueueFactory<?> queueFactory) {
        this.queueFactory = queueFactory;
    }

    @Inject
    public void setMailProcessor(MailProcessor mailProcessor) {
        this.mailProcessor = mailProcessor;
    }

    @Override
    public void configure(HierarchicalConfiguration config) {
        numThreads = config.getInt("threads", 100);
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
            .publishOn(spooler)
            .flatMap(this::handleOnQueueItem)
            .onErrorContinue((throwable, item) -> LOGGER.error("Exception processing mail while spooling {}", item, throwable))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    private Mono<Void> handleOnQueueItem(MailQueueItem queueItem) {
        TimeMetric timeMetric = metricFactory.timer(SPOOL_PROCESSING);
        try {
            processingActive.incrementAndGet();
            return processMail(queueItem);
        } catch (Throwable e) {
            return Mono.error(e);
        } finally {
            processingActive.decrementAndGet();
            timeMetric.stopAndPublish();
        }
    }

    private Mono<Void> processMail(MailQueueItem queueItem) throws MailQueue.MailQueueException {
        Mail mail = queueItem.getMail();
        LOGGER.debug("==== Begin processing mail {} ====", mail.getName());
        try {
            mailProcessor.service(mail);
            queueItem.done(true);
            return Mono.empty();
        } catch (Exception e) {
            queueItem.done(false);
            return Mono.error(e);
        } finally {
            LOGGER.debug("==== End processing mail {} ====", mail.getName());
            LifecycleUtil.dispose(mail);
        }
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
