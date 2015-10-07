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
package org.apache.james.util.concurrent;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * {@link ScheduledThreadPoolExecutor} which expose statistics via JMX
 * 
 *
 */
public class JMXEnabledScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor implements JMXEnabledScheduledThreadPoolExecutorMBean {

    private final String jmxPath;
    private final List<Runnable> inProgress = Collections.synchronizedList(new ArrayList<Runnable>());
    private final ThreadLocal<Long> startTime = new ThreadLocal<Long>();
    private long totalTime;
    private int totalTasks;
    private MBeanServer mbeanServer;
    private String mbeanName;

    public JMXEnabledScheduledThreadPoolExecutor(int corePoolSize, NamedThreadFactory threadFactory, String jmxPath) {
        super(corePoolSize, threadFactory);

        this.jmxPath = jmxPath;
        registerMBean();
    }

    public JMXEnabledScheduledThreadPoolExecutor(int corePoolSize, String jmxPath, String name) {
        super(corePoolSize, new NamedThreadFactory(name));

        this.jmxPath = jmxPath;
        registerMBean();
    }

    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        inProgress.add(r);
        startTime.set(System.currentTimeMillis());
    }

    protected void afterExecute(Runnable r, Throwable t) {
        long time = System.currentTimeMillis() - startTime.get();
        synchronized (this) {
            totalTime += time;
            ++totalTasks;
        }
        inProgress.remove(r);
        super.afterExecute(r, t);
    }

    private void registerMBean() {
        if (jmxPath != null) {
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
            mbeanName = jmxPath + ",threadpool=" + ((NamedThreadFactory) getThreadFactory()).getName();
            try {
                mbeanServer.registerMBean(this, new ObjectName(mbeanName));
            } catch (Exception e) {
                throw new RuntimeException("Unable to register mbean", e);
            }
        }
    }

    private void unregisterMBean() {
        if (jmxPath != null) {
            try {
                mbeanServer.unregisterMBean(new ObjectName(mbeanName));

            } catch (Exception e) {
                throw new RuntimeException("Unable to unregister mbean", e);
            }
        }
    }

    @Override
    public synchronized void shutdown() {
        // synchronized, because there is no way to access super.mainLock, which
        // would be
        // the preferred way to make this threadsafe
        if (!isShutdown()) {
            unregisterMBean();
        }
        super.shutdown();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        // synchronized, because there is no way to access super.mainLock, which
        // would be
        // the preferred way to make this threadsafe
        if (!isShutdown()) {
            unregisterMBean();
        }
        return super.shutdownNow();
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getTotalTasks()
     */
    public synchronized int getTotalTasks() {
        return totalTasks;
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getAverageTaskTime()
     */
    public synchronized double getAverageTaskTime() {
        return (totalTasks == 0) ? 0 : totalTime / totalTasks;
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getActiveThreads()
     */
    public int getActiveThreads() {
        return getPoolSize();
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getActiveTasks()
     */
    public int getActiveTasks() {
        return getActiveCount();
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getQueuedTasks()
     */
    public int getQueuedTasks() {
        return getQueue().size();
    }

    /**
     * @see org.apache.james.util.concurrent.JMXEnabledThreadPoolExecutorMBean#getMaximalThreads()
     */
    public int getMaximalThreads() {
        return getMaximumPoolSize();
    }
}
