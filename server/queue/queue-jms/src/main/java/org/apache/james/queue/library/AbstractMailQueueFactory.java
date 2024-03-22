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
package org.apache.james.queue.library;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueManagementMBean;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

/**
 * {@link MailQueueFactory} abstract base class which take care of register the
 * {@link MailQueue} implementations via JMX (if possible)
 */
public abstract class AbstractMailQueueFactory<T extends MailQueue> implements MailQueueFactory<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMailQueueFactory.class);

    public static final String MBEAN_NAME_QUEUE_PREFIX = "org.apache.james:type=component,name=queue,queue=";

    protected final Map<MailQueueName, T> queues = new HashMap<>();
    private boolean useJMX = true;
    private MBeanServer mbeanServer;
    private final List<String> mbeans = new ArrayList<>();

    public void setUseJMX(boolean useJMX) {
        this.useJMX = useJMX;
    }

    @VisibleForTesting
    void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }

    @PostConstruct
    public void init() {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public Set<MailQueueName> listCreatedMailQueues() {
        return queues.values()
            .stream()
            .map(MailQueue::getName)
            .collect(ImmutableSet.toImmutableSet());
    }

    @PreDestroy
    public synchronized void destroy() {
        for (String mbean : mbeans) {
            try {
                mbeanServer.unregisterMBean(new ObjectName(mbean));
            } catch (Exception e) {
                LOGGER.error("Error while destroying AbstractMailQueueFactory : ", e);
            }
        }
        mbeans.clear();
        for (MailQueue mailQueue : queues.values()) {
            LifecycleUtil.dispose(mailQueue);
        }

    }

    @Override
    public final synchronized Optional<T> getQueue(MailQueueName name, PrefetchCount prefetchCount) {
        return Optional.ofNullable(queues.get(name));
    }

    @Override
    public synchronized T createQueue(MailQueueName name, PrefetchCount prefetchCount) {
        return getQueue(name, prefetchCount).orElseGet(() -> createAndRegisterQueue(name));
    }

    private T createAndRegisterQueue(MailQueueName name) {
        T queue = createCacheableMailQueue(name);
        if (useJMX) {
            registerMBean(name, queue);
        }
        queues.put(name, queue);
        return queue;
    }

    /**
     * Create a {@link MailQueue} for the given name that happens to do nothing on close()
     * to be able to cache the instance
     */
    protected abstract T createCacheableMailQueue(MailQueueName name);

    protected synchronized void registerMBean(MailQueueName queuename, MailQueue queue) {

        String mbeanName = MBEAN_NAME_QUEUE_PREFIX + queuename.asString();
        try {
            MailQueueManagementMBean mbean = null;
            if (queue instanceof ManageableMailQueue) {
                mbean = new MailQueueManagement((ManageableMailQueue) queue);
            } else if (queue instanceof MailQueueManagementMBean) {
                mbean = (MailQueueManagementMBean) queue;
            }
            if (mbean != null) {
                mbeanServer.registerMBean(mbean, new ObjectName(mbeanName));
                mbeans.add(mbeanName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to register mbean", e);
        }

    }

    protected synchronized void unregisterMBean(String mbeanName) {
        try {
            mbeanServer.unregisterMBean(new ObjectName(mbeanName));
            mbeans.remove(mbeanName);
        } catch (Exception e) {
            throw new RuntimeException("Unable to unregister mbean", e);
        }

    }
    
}
