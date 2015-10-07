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

package org.apache.james.mailetcontainer.impl.jmx;

import java.util.concurrent.atomic.AtomicLong;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean;

/**
 * Wrapper which helps to expose JMX statistics for {@link MailProcessor} and
 * {@link org.apache.james.mailetcontainer.impl.camel.CamelProcessor} implementations
 */
public class MailProcessorManagement extends StandardMBean implements MailProcessorManagementMBean {
    private String processorName;
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong fastestProcessing = new AtomicLong(-1);
    private final AtomicLong slowestProcessing = new AtomicLong(-1);
    private final AtomicLong lastProcessing = new AtomicLong(-1);

    public MailProcessorManagement(String processorName) throws NotCompliantMBeanException {
        super(MailProcessorManagementMBean.class);
        this.processorName = processorName;
    }

    /**
     * Update the stats
     * 
     * @param processTime
     * @param success
     */
    public void update(long processTime, boolean success) {
        long fastest = fastestProcessing.get();

        if (fastest > processTime || fastest == -1) {
            fastestProcessing.set(processTime);
        }

        if (slowestProcessing.get() < processTime) {
            slowestProcessing.set(processTime);
        }
        if (success) {
            successCount.incrementAndGet();
        } else {
            errorCount.incrementAndGet();
        }

        lastProcessing.set(processTime);

    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getHandledMailCount()
     */
    public long getHandledMailCount() {
        return getSuccessCount() + getErrorCount();
    }

    /*
     * TODO
     * Is deprecated or dead code? Can't find an interface defining this method
     * 
     * @see
     * org.apache.james.mailetcontainer.api.jmx.MailProcessorDetailMBean#getName()
     */
    public String getName() {
        return processorName;
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getFastestProcessing()
     */
    public long getFastestProcessing() {
        return fastestProcessing.get();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getSlowestProcessing()
     */
    public long getSlowestProcessing() {
        return slowestProcessing.get();
    }

    /**
     * @see
     * org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getErrorCount()
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * @see
     * org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getSuccessCount()
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getLastProcessing()
     */
    public long getLastProcessing() {
        return lastProcessing.get();
    }

}
