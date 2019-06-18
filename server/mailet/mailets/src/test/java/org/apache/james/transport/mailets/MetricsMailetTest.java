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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MetricsMailetTest {

    public static final String MAILET_NAME = "Metric test";
    public static final String METRIC_NAME = "metricName";
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private RecordingMetricFactory metricFactory;
    private MetricsMailet mailet;

    @Before
    public void setUp() throws Exception {
        metricFactory = new RecordingMetricFactory();
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
            .setProperty(MetricsMailet.METRIC_NAME, METRIC_NAME)
            .build());

        mailet.service(FakeMail.builder().name("name").build());

        assertThat(metricFactory.countFor(METRIC_NAME))
            .isEqualTo(1);
    }

}
