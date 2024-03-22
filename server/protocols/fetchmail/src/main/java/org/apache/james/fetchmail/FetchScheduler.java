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

package org.apache.james.fetchmail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.concurrent.JMXEnabledScheduledThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to instantiate and schedule a set of mail fetching tasks
 */
public class FetchScheduler implements FetchSchedulerMBean, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchScheduler.class);

    /**
     * Configuration object for this service
     */
    private HierarchicalConfiguration<ImmutableNode> conf;

    /**
     * Whether this service is enabled.
     */
    private volatile boolean enabled = false;

    private final List<ScheduledFuture<?>> schedulers = new ArrayList<>();

    private DNSService dns;

    private UsersRepository urepos;

    private MailQueueFactory<?> queueFactory;

    private DomainList domainList;
    private MailQueue queue;

    @Inject
    public void setMailQueueFactory(MailQueueFactory<?> queueFactory) {
        this.queueFactory = queueFactory;
    }

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    @Inject
    public void setUsersRepository(UsersRepository urepos) {
        this.urepos = urepos;
    }

    @Inject
    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    @Override
    public final void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        this.conf = config;
    }

    @PostConstruct
    public void init() throws Exception {
        enabled = conf.getBoolean("[@enabled]", false);
        if (enabled) {
            int numThreads = conf.getInt("threads", 5);
            String jmxName = conf.getString("jmxName", "fetchmail");
            String jmxPath = "org.apache.james:type=component,name=" + jmxName + ",sub-type=threadpool";

            /*
      The scheduler service that is used to trigger fetch tasks.
     */
            ScheduledExecutorService scheduler = new JMXEnabledScheduledThreadPoolExecutor(numThreads, jmxPath, "scheduler");
            queue = queueFactory.createQueue(MailQueueFactory.SPOOL);

            List<HierarchicalConfiguration<ImmutableNode>> fetchConfs = conf.configurationsAt("fetch");
            for (HierarchicalConfiguration<ImmutableNode> fetchConf : fetchConfs) {
                // read configuration
                Long interval = fetchConf.getLong("interval");

                FetchMail fetcher = new FetchMail();

                fetcher.setDNSService(dns);
                fetcher.setUsersRepository(urepos);
                fetcher.setMailQueue(queue);
                fetcher.setDomainList(domainList);

                fetcher.configure(fetchConf);

                // initialize scheduling
                schedulers.add(scheduler.scheduleWithFixedDelay(fetcher, 0, interval, TimeUnit.MILLISECONDS));
            }

            LOGGER.info("FetchMail Started");
        } else {
            LOGGER.info("FetchMail Disabled");
        }
    }

    @PreDestroy
    public void dispose() throws IOException {
        if (enabled) {
            LOGGER.info("FetchMail dispose...");
            queue.close();
            for (ScheduledFuture<?> scheduler1 : schedulers) {
                scheduler1.cancel(false);
            }
            LOGGER.info("FetchMail ...dispose end");
        }
    }

    /**
     * Describes whether this service is enabled by configuration.
     *
     * @return is the service enabled.
     */
    @Override
    public final boolean isEnabled() {
        return enabled;
    }

}
