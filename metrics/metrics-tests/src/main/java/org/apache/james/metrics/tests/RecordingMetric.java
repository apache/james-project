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

package org.apache.james.metrics.tests;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.metrics.api.Metric;

public class RecordingMetric implements Metric {
    private final AtomicInteger value;

    RecordingMetric() {
        this(new AtomicInteger());
    }

    RecordingMetric(AtomicInteger value) {
        this.value = value;
    }

    @Override
    public void increment() {
        value.incrementAndGet();
    }

    @Override
    public void decrement() {
        value.decrementAndGet();
    }

    @Override
    public void add(int i) {
        value.addAndGet(i);
    }

    @Override
    public void remove(int i) {
        value.addAndGet(-1 * i);
    }

    @Override
    public long getCount() {
        return value.get();
    }
}
