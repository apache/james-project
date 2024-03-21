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

import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

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


    private static class Runner {
        private final AtomicInteger processingActive = new AtomicInteger(0);
        private final MetricFactory metricFactory;
        private final MailProcessor mailProcessor;
        private final MailRepository errorRepository;
        private final reactor.core.Disposable disposable;
        private final MailQueue queue;
        private final Configuration configuration;
        private final Scheduler scheduler;
        private final Scheduler queueScheduler;

        private Runner(MetricFactory metricFactory, GaugeRegistry gaugeRegistry, MailProcessor mailProcessor,
                       MailRepository errorRepository, MailQueue queue, Configuration configuration) {
            this.metricFactory = metricFactory;
            this.mailProcessor = mailProcessor;
            this.errorRepository = errorRepository;
            this.queue = queue;
            this.configuration = configuration;

            scheduler = Schedulers.newBoundedElastic(configuration.getConcurrencyLevel() + 1, DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
                "spooler");

            queueScheduler = Schedulers.newBoundedElastic(1, DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
                "queueScheduler");

            this.disposable = run(queue);

            gaugeRegistry.register(SPOOL_PROCESSING + ".inFlight",
                processingActive::get);
        }

        private reactor.core.Disposable run(MailQueue queue) {
            return Flux.from(queue.deQueue())
                .flatMap(item -> handleOnQueueItem(item).subscribeOn(scheduler), configuration.getConcurrencyLevel())
                .onErrorContinue((throwable, item) -> LOGGER.error("Exception processing mail while spooling {}", item, throwable))
                .subscribeOn(queueScheduler)
                .subscribe();
        }

        private Mono<Void> handleOnQueueItem(MailQueueItem queueItem) {
            TimeMetric timeMetric = metricFactory.timer(SPOOL_PROCESSING);
            return Mono.fromCallable(processingActive::incrementAndGet)
                .flatMap(ignore -> processMail(queueItem))
                .doOnSuccess(any -> timeMetric.stopAndPublish())
                .doOnTerminate(processingActive::decrementAndGet);
        }

        private Mono<Void> processMail(MailQueueItem queueItem) {
            return Mono
                .using(
                    queueItem::getMail,
                    mail -> Mono.fromRunnable(() -> performProcessMail(queueItem, mail)),
                    LifecycleUtil::dispose);
        }

        private void performProcessMail(MailQueueItem queueItem, Mail mail) {
            LOGGER.debug("==== Begin processing mail {} ====", mail.getName());
            ImmutableList<MailAddress> originalRecipients = ImmutableList.copyOf(mail.getRecipients());
            try {
                mailProcessor.service(mail);

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Thread has been interrupted");
                }
                queueItem.done(MailQueueItem.CompletionStatus.SUCCESS);
            } catch (Exception e) {
                handleError(queueItem, mail, originalRecipients, e);
            } finally {
                LOGGER.debug("==== End processing mail {} ====", mail.getName());
            }
        }

        private void handleError(MailQueueItem queueItem, Mail mail, ImmutableList<MailAddress> originalRecipients, Exception processingException) {
            int failureCount = computeFailureCount(mail);

            // Restore original recipients
            queueItem.getMail().setRecipients(originalRecipients);

            try {
                if (failureCount > MAXIMUM_FAILURE_COUNT) {
                    LOGGER.error("Failed {} processing {} consecutive times. Abort. Mail is saved in {}", mail.getName(), failureCount, configuration.getErrorRepositoryURL().asString());
                    storeInErrorRepository(queueItem);
                } else {
                    LOGGER.error("Failed {} processing {} consecutive times. Mail is requeued with increased failure count.", mail.getName(), failureCount, processingException);
                    reEnqueue(queueItem, failureCount);
                }
            } catch (Exception nestedE) {
                LOGGER.error("Could not apply standard error handling for {}, defaulting to nack", mail.getName(), nestedE);
                nack(queueItem, processingException);
            }
        }

        private int computeFailureCount(Mail mail) {
            Integer previousFailureCount = mail.getAttribute(MAIL_PROCESSING_ERROR_COUNT)
                .flatMap(attribute -> attribute.getValue().valueAs(Integer.class))
                .orElse(0);
            return previousFailureCount + 1;
        }

        private void reEnqueue(MailQueueItem queueItem, int failureCount) throws MailQueue.MailQueueException {
            Mail mail = queueItem.getMail();
            mail.setAttribute(new Attribute(MAIL_PROCESSING_ERROR_COUNT, AttributeValue.of(failureCount)));
            queue.enQueue(mail);
            queueItem.done(MailQueueItem.CompletionStatus.SUCCESS);
        }

        private void storeInErrorRepository(MailQueueItem queueItem) throws MessagingException {
            errorRepository.store(queueItem.getMail());
            queueItem.done(MailQueueItem.CompletionStatus.SUCCESS);
        }

        private void nack(MailQueueItem queueItem, Exception processingException) {
            try {
                queueItem.done(MailQueueItem.CompletionStatus.REJECT);
            } catch (MailQueue.MailQueueException ex) {
                throw new RuntimeException(processingException);
            }
        }

        public void dispose() {
            LOGGER.info("start dispose() ...");
            LOGGER.info("Cancel queue consumption...");
            queueScheduler.dispose();
            LOGGER.info("Queue consumption canceled, shutting down processor threads...");
            scheduler.disposeGracefully()
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> Mono.empty())
                .block();
            disposable.dispose();
            LOGGER.info("Thread shutdown completed. Turning off mail queue.");
            try {
                queue.close();
            } catch (IOException e) {
                LOGGER.debug("error closing queue", e);
            }
        }

        public int getCurrentSpoolCount() {
            return processingActive.get();
        }
    }

    public static class Configuration {
        public static Configuration from(MailRepositoryStore mailRepositoryStore, HierarchicalConfiguration<ImmutableNode> config) {
            int concurrencyLevel = config.getInt("threads", 100);
            MailRepositoryUrl errorRepositoryURL = Optional.ofNullable(config.getString("errorRepository", null))
                .map(MailRepositoryUrl::from)
                .orElseGet(() -> MailRepositoryUrl.fromPathAndProtocol(
                    mailRepositoryStore.defaultProtocol()
                        .orElseThrow(() -> new IllegalStateException("Cannot retrieve mailRepository URL, you need to configure an `errorRepository` property for the spooler.0")),
                    ERROR_REPOSITORY_PATH));

            return new Configuration(concurrencyLevel, errorRepositoryURL);
        }

        private final int concurrencyLevel;
        private final MailRepositoryUrl errorRepositoryURL;

        public Configuration(int concurrencyLevel, MailRepositoryUrl errorRepositoryURL) {
            Preconditions.checkArgument(concurrencyLevel >= 0, "'threads' needs to be greater than or equal to zero");
            
            this.concurrencyLevel = concurrencyLevel;
            this.errorRepositoryURL = errorRepositoryURL;
        }

        public int getConcurrencyLevel() {
            return concurrencyLevel;
        }

        public boolean isEnabled() {
            return concurrencyLevel > 0;
        }

        public MailRepositoryUrl getErrorRepositoryURL() {
            return errorRepositoryURL;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("concurrencyLevel", concurrencyLevel)
                .add("errorRepositoryURL", errorRepositoryURL)
                .toString();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JamesMailSpooler.class);

    public static final String SPOOL_PROCESSING = "spoolProcessing";
    public static final AttributeName MAIL_PROCESSING_ERROR_COUNT = AttributeName.of("mail-processing-error-count");
    public static final MailRepositoryPath ERROR_REPOSITORY_PATH = MailRepositoryPath.from("var/mail/error");
    public static final int MAXIMUM_FAILURE_COUNT = 5;

    /**
     * concurrency level to use for dequeuing mails from spool, allows to throttle resources dedicated to that async
     * process.
     */
    private final MetricFactory metricFactory;
    private final GaugeRegistry gaugeRegistry;
    private final MailProcessor mailProcessor;
    private final MailRepositoryStore mailRepositoryStore;
    private final MailQueueFactory<?> queueFactory;

    private Configuration configuration;
    private Optional<Runner> runner;
    private MailQueue queue;

    @Inject
    public JamesMailSpooler(MetricFactory metricFactory, GaugeRegistry gaugeRegistry, MailProcessor mailProcessor, MailRepositoryStore mailRepositoryStore, MailQueueFactory<?> queueFactory) {
        this.metricFactory = metricFactory;
        this.gaugeRegistry = gaugeRegistry;
        this.mailProcessor = mailProcessor;
        this.mailRepositoryStore = mailRepositoryStore;
        this.queueFactory = queueFactory;
    }

    public void restart() {
        Optional<Runner> previous = runner;
        runner = Optional.of(new Runner(metricFactory, gaugeRegistry, mailProcessor, errorRepository(), queue, configuration));
        previous.ifPresent(Runner::dispose);
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        configure(Configuration.from(mailRepositoryStore, config));
    }

    public void configure(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Initializes the spool manager.
     */
    @PostConstruct
    public void init() {
        if (configuration.isEnabled()) {
            LOGGER.info("init...");
            LOGGER.info("Concurrency level is {}", configuration.getConcurrencyLevel());
            queue = queueFactory.createQueue(MailQueueFactory.SPOOL, MailQueueFactory.prefetchCount(configuration.getConcurrencyLevel()));
            runner = Optional.of(new Runner(metricFactory, gaugeRegistry,
                mailProcessor, errorRepository(), queue, configuration));
            LOGGER.info("Spooler started");
        } else {
            LOGGER.info("Spooler had been deactivated. To enable it set 'threads' count to a value greater than zero");
        }
    }

    private MailRepository errorRepository() {
        try {
            return mailRepositoryStore.select(configuration.getErrorRepositoryURL());
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new RuntimeException(e);
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
        runner.ifPresent(Runner::dispose);
    }

    @Override
    public int getThreadCount() {
        return configuration.getConcurrencyLevel();
    }

    @Override
    public int getCurrentSpoolCount() {
        return runner.map(Runner::getCurrentSpoolCount).orElse(0);
    }
}
