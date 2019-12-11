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

import com.codahale.metrics.Counter;

public class DropWizardMetric implements Metric {

    private final Counter counter;

    public DropWizardMetric(Counter counter) {
        this.counter = counter;
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
        return counter.getCount();
    }
}
