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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.google.common.annotations.VisibleForTesting;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueManagementMBean;
import org.apache.james.queue.api.ManageableMailQueue;
import org.slf4j.Logger;

/**
 * {@link MailQueueFactory} abstract base class which take care of register the
 * {@link MailQueue} implementations via JMX (if possible)
 */
public abstract class AbstractMailQueueFactory implements MailQueueFactory, LogEnabled {

    public static final String MBEAN_NAME_QUEUE_PREFIX = "org.apache.james:type=component,name=queue,queue=";

    protected final Map<String, MailQueue> queues = new HashMap<String, MailQueue>();
    protected Logger log;
    private boolean useJMX = true;
    private MBeanServer mbeanServer;
    private final List<String> mbeans = new ArrayList<String>();

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

    @PreDestroy
    public synchronized void destroy() {
        for (String mbean : mbeans) {
            try {
                mbeanServer.unregisterMBean(new ObjectName(mbean));
            } catch (Exception e) {
                log.error("Error while destroying AbstractMailQueueFactory : ", e);
            }
        }
        mbeans.clear();
        for (MailQueue mailQueue : queues.values()) {
            LifecycleUtil.dispose(mailQueue);
        }

    }

    @Override
    public synchronized final MailQueue getQueue(String name) {
        
        MailQueue queue = queues.get(name);

        if (queue == null) {
            queue = createMailQueue(name);
            if (useJMX) {
                registerMBean(name, queue);

            }
            queues.put(name, queue);
        }

        return queue;
    }

    /**
     * Create a {@link MailQueue} for the given name
     * 
     * @param name
     * @return queue
     */
    protected abstract MailQueue createMailQueue(String name);

    protected synchronized void registerMBean(String queuename, MailQueue queue) {

        String mbeanName = MBEAN_NAME_QUEUE_PREFIX + queuename;
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

    @Override
    public void setLog(Logger log) {
        this.log = log;
    }
    
}
