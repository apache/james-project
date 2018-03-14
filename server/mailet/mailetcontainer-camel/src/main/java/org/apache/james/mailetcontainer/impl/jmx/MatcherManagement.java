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

import org.apache.james.mailetcontainer.api.jmx.MatcherManagementMBean;
import org.apache.mailet.MatcherConfig;

public final class MatcherManagement extends StandardMBean implements MatcherManagementMBean {
    private final MatcherConfig matcherConfig;
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong fastestProcessing = new AtomicLong(-1);
    private final AtomicLong slowestProcessing = new AtomicLong(-1);
    private final AtomicLong matchedCount = new AtomicLong(0);
    private final AtomicLong notMatchedCount = new AtomicLong(0);
    private final AtomicLong lastProcessing = new AtomicLong(-1);

    public MatcherManagement(MatcherConfig matcherConfig) throws NotCompliantMBeanException {
        super(MatcherManagementMBean.class);
        this.matcherConfig = matcherConfig;

    }

    public void update(long processTime, boolean success, boolean matched) {
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
        if (matched) {
            matchedCount.incrementAndGet();
        } else {
            notMatchedCount.incrementAndGet();
        }

        lastProcessing.set(processTime);

    }

    @Override
    public String getMatcherName() {
        return matcherConfig.getMatcherName();
    }

    @Override
    public String getMatcherCondition() {
        return matcherConfig.getCondition();
    }

    @Override
    public long getErrorCount() {
        return errorCount.get();
    }

    @Override
    public long getFastestProcessing() {
        return fastestProcessing.get();
    }

    @Override
    public long getHandledMailCount() {
        return getSuccessCount() + getErrorCount();

    }

    @Override
    public long getSlowestProcessing() {
        return slowestProcessing.get();
    }

    @Override
    public long getSuccessCount() {
        return successCount.get();
    }

    @Override
    public long getMatchedCount() {
        return matchedCount.get();
    }

    @Override
    public long getNotMatchedCount() {
        return notMatchedCount.get();
    }

    @Override
    public long getLastProcessing() {
        return lastProcessing.get();
    }
}
