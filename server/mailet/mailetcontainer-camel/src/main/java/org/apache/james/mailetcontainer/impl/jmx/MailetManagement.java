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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.mailetcontainer.api.jmx.MailetManagementMBean;
import org.apache.mailet.MailetConfig;

public final class MailetManagement extends StandardMBean implements MailetManagementMBean {

    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong fastestProcessing = new AtomicLong(-1);
    private final AtomicLong slowestProcessing = new AtomicLong(-1);
    private final AtomicLong lastProcessing = new AtomicLong(-1);

    private final MailetConfig config;

    public MailetManagement(MailetConfig config) throws NotCompliantMBeanException {
        super(MailetManagementMBean.class);
        this.config = config;

    }

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
     * @see
     * org.apache.james.mailetcontainer.api.jmx.MailetManagementMBean#getMailetName()
     */
    public String getMailetName() {
        return config.getMailetName();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailetManagementMBean#getMailetParameters()
     */
    public String[] getMailetParameters() {
        List<String> parameterList = new ArrayList<>();
        Iterator<String> iterator = config.getInitParameterNames();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = config.getInitParameter(name);
            parameterList.add(name + "=" + value);
        }
        return parameterList.toArray(new String[parameterList.size()]);
    }

    /**
     * @see
     * org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getErrorCount()
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getFastestProcessing()
     */
    public long getFastestProcessing() {
        return fastestProcessing.get();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getHandledMailCount()
     */
    public long getHandledMailCount() {
        return getErrorCount() + getSuccessCount();
    }

    /**
     * @see org.apache.james.mailetcontainer.api.jmx.MailProcessorManagementMBean#getSlowestProcessing()
     */
    public long getSlowestProcessing() {
        return slowestProcessing.get();
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
