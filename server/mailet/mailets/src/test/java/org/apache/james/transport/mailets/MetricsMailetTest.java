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

package org.apache.james.transport.mailets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MetricsMailetTest {

    public static final String MAILET_NAME = "Metric test";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MetricFactory metricFactory;
    private Metric metric;
    private MetricsMailet mailet;

    @Before
    public void setUp() throws Exception {
        metricFactory = mock(MetricFactory.class);
        metric = mock(Metric.class);
        when(metricFactory.generate(anyString())).thenReturn(metric);

        mailet = new MetricsMailet(metricFactory);
    }

    @Test
    public void initShouldThrowWhenMetricNameIsNotGiven() throws Exception {
        expectedException.expect(NullPointerException.class);

        mailet.init(FakeMailetConfig.builder().mailetName(MAILET_NAME).build());
    }

    @Test
    public void serviceShouldIncrementMetricCounter() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .setProperty(MetricsMailet.METRIC_NAME, "metricName")
            .build());

        mailet.service(FakeMail.builder().build());

        verify(metric).increment();
        verifyNoMoreInteractions(metric);
    }

}
