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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutor;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the mail spool. This class is responsible for retrieving messages
 * from the spool, directing messages to the appropriate processor, and removing
 * them from the spool when processing is complete.
 */
public class JamesMailSpooler implements Runnable, Disposable, Configurable, MailSpoolerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesMailSpooler.class);

    public static final String SPOOL_PROCESSING = "spoolProcessing";
    private MailQueue queue;

    /**
     * The number of threads used to move mail through the spool.
     */
    private int numThreads;

    /**
     * Number of active threads
     */
    private final AtomicInteger numActive = new AtomicInteger(0);

    private final AtomicInteger processingActive = new AtomicInteger(0);

    /**
     * Spool threads are active
     */
    private final AtomicBoolean active = new AtomicBoolean(false);

    private final MetricFactory metricFactory;

    /**
     * Spool threads
     */
    private ExecutorService dequeueService;

    private ExecutorService workerService;

    /**
     * The mail processor
     */
    private MailProcessor mailProcessor;

    private MailQueueFactory<?> queueFactory;

    private int numDequeueThreads;

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

    /**
     * @see org.apache.james.lifecycle.api.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        numDequeueThreads = config.getInt("dequeueThreads", 2);

        numThreads = config.getInt("threads", 100);
    }

    /**
     * Initializes the spool manager.
     */
    @PostConstruct
    public void init() {
        LOGGER.info("{} init...", getClass().getName());

        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);

        LOGGER.info("{} uses {} Thread(s)", getClass().getName(), numThreads);

        active.set(true);
        workerService = JMXEnabledThreadPoolExecutor.newFixedThreadPool("org.apache.james:type=component,component=mailetcontainer,name=mailspooler,sub-type=threadpool", "spooler", numThreads);
        dequeueService = JMXEnabledThreadPoolExecutor.newFixedThreadPool("org.apache.james:type=component,component=mailetcontainer,name=mailspooler,sub-type=threadpool", "dequeuer", numDequeueThreads);

        for (int i = 0; i < numDequeueThreads; i++) {
            Thread reader = new Thread(this, "Dequeue Thread #" + i);
            dequeueService.execute(reader);
        }
    }

    /**
     * This routinely checks the message spool for messages, and processes them
     * as necessary
     */
    @Override
    public void run() {
        LOGGER.info("Run {}: {}", getClass().getName(), Thread.currentThread().getName());
        LOGGER.info("Queue={}", queue);

        while (active.get()) {

            final MailQueueItem queueItem;
            try {
                queueItem = queue.deQueue();
                workerService.execute(() -> {
                    TimeMetric timeMetric = metricFactory.timer(SPOOL_PROCESSING);
                    try {
                        numActive.incrementAndGet();

                        // increase count
                        processingActive.incrementAndGet();

                        Mail mail = queueItem.getMail();
                        LOGGER.debug("==== Begin processing mail {} ====", mail.getName());

                        try {
                            mailProcessor.service(mail);
                            queueItem.done(true);
                        } catch (Exception e) {
                            if (active.get()) {
                                LOGGER.error("Exception processing mail while spooling", e);
                            }
                            queueItem.done(false);

                        } finally {
                            LifecycleUtil.dispose(mail);
                            mail = null;
                        }
                    } catch (Throwable e) {
                        if (active.get()) {
                            LOGGER.error("Exception processing mail while spooling", e);

                        }
                    } finally {
                        processingActive.decrementAndGet();
                        numActive.decrementAndGet();
                        timeMetric.stopAndPublish();
                    }

                });
            } catch (MailQueueException e1) {
                if (active.get()) {
                    LOGGER.error("Exception dequeue mail", e1);
                }
            } catch (InterruptedException interrupted) {
                //MailSpooler is stopping
            }
        }
        LOGGER.info("Stop {} : {}", getClass().getName(), Thread.currentThread().getName());
    }

    /**
     * The dispose operation is called at the end of a components lifecycle.
     * Instances of this class use this method to release and destroy any
     * resources that they own.
     * <p/>
     * This implementation shuts down the LinearProcessors managed by this
     * JamesSpoolManager
     *
     * @see org.apache.james.lifecycle.api.Disposable#dispose()
     */
    @PreDestroy
    @Override
    public void dispose() {
        LOGGER.info("{} dispose...", getClass().getName());
        active.set(false); // shutdown the threads
        dequeueService.shutdownNow();
        workerService.shutdown();

        long stop = System.currentTimeMillis() + 60000;
        // give the spooler threads one minute to terminate gracefully
        while (numActive.get() != 0 && stop > System.currentTimeMillis()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        LOGGER.info("{} thread shutdown completed.", getClass().getName());
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
