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
import org.apache.james.metrics.api.MetricContract;
import org.junit.jupiter.api.BeforeEach;

import com.codahale.metrics.MetricRegistry;

class DropWizardMetricTest implements MetricContract {

    private static final String METRIC_NAME = "myMetric";

    private DropWizardMetric testee;

    @BeforeEach
    void setUp() {
        MetricRegistry registry = new MetricRegistry();
        testee = new DropWizardMetric(registry.counter(METRIC_NAME));
    }

    @Override
    public Metric testee() {
        return testee;
    }
}