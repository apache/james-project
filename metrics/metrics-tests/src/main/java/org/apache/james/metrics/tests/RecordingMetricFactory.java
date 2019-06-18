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

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class RecordingMetricFactory implements MetricFactory {
    private final Multimap<String, Duration> executionTimes = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    private final ConcurrentHashMap<String, Integer> counters = new ConcurrentHashMap<>();

    @Override
    public Metric generate(String name) {
        return new RecordingMetric(executionValue -> counters.put(name, executionValue));
    }

    @Override
    public TimeMetric timer(String name) {
        return new RecordingTimeMetric(name, executionTime -> {
            synchronized (executionTimes) {
                executionTimes.put(name, executionTime);
            }
        });
    }

    public Collection<Duration> executionTimesFor(String name) {
        synchronized (executionTimes) {
            return executionTimes.get(name);
        }
    }

    public Multimap<String, Duration> executionTimesForPrefixName(String prefixName) {
        synchronized (executionTimes) {
            return Multimaps.filterKeys(executionTimes, key -> key.startsWith(prefixName));
        }
    }

    public int countFor(String name) {
        return counters.getOrDefault(name, 0);
    }

    public Map<String, Integer> countForPrefixName(String prefixName) {
        return counters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefixName))
            .collect(Guavate.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
