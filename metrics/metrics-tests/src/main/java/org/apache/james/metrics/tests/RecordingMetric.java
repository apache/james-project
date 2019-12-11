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
import java.util.function.Consumer;

import org.apache.james.metrics.api.Metric;

public class RecordingMetric implements Metric {
    private final AtomicInteger value;
    private final Consumer<Integer> publishCallback;

    public RecordingMetric(Consumer<Integer> publishCallback) {
        this.value = new AtomicInteger();
        this.publishCallback = publishCallback;
    }

    @Override
    public void increment() {
        publishCallback.accept(value.incrementAndGet());
    }

    @Override
    public void decrement() {
        publishCallback.accept(value.decrementAndGet());
    }

    @Override
    public void add(int i) {
        publishCallback.accept(value.addAndGet(i));
    }

    @Override
    public void remove(int i) {
        publishCallback.accept(value.addAndGet(-1 * i));
    }

    @Override
    public long getCount() {
        return value.get();
    }
}
