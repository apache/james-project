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

package org.apache.james.metrics.dropwizard;

import org.apache.james.metrics.api.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;

public class DropWizardMetric implements Metric {

    private static final Logger LOGGER = LoggerFactory.getLogger(DropWizardMetric.class);

    private final Counter counter;
    private final String metricName;

    public DropWizardMetric(Counter counter, String metricName) {
        this.counter = counter;
        this.metricName = metricName;
    }

    @Override
    public void increment() {
        counter.inc();
    }

    @Override
    public void decrement() {
        counter.dec();
    }

    @Override
    public void add(int value) {
        counter.inc(value);
    }

    @Override
    public void remove(int value) {
        counter.dec(value);
    }

    @Override
    public long getCount() {
        long value = counter.getCount();
        if (value < 0) {
            LOGGER.error("counter value({}) of the metric '{}' should not be a negative number", value, metricName);
            return 0;
        }

        return value;
    }
}
