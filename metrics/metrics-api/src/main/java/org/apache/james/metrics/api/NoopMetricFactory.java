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
package org.apache.james.metrics.api;

import java.util.function.Supplier;

public class NoopMetricFactory implements MetricFactory {

    @Override
    public Metric generate(String name) {
        return new NoopMetric();
    }

    public static class NoopMetric implements Metric  {

        @Override
        public void increment() {
        }

        @Override
        public void decrement() {
        }
        
    }

    @Override
    public TimeMetric timer(String name) {
        return new NoopTimeMetric();
    }

    public static class NoopTimeMetric implements TimeMetric {

        @Override
        public String name() {
            return "";
        }


        @Override
        public long stopAndPublish() {
            return 0;
        }
    }

    @Override
    public <T> T withMetric(String name, Supplier<T> operation) {
        return operation.get();
    }
}
