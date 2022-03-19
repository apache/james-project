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

import javax.inject.Inject;

import jakarta.mail.MessagingException;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Preconditions;

/**
 * This Metrics mailet increments a counter on every incoming emails.
 *
 * This counter is accessible via JMX.
 *
 * Example :
 *
 * &lt;mailet match="all" class="MetricsMailet"&gt;
 *     &lt;metricName&gt;relayDenied&lt;/metricName&gt;
 * &lt;/mailet&gt;
 *
 * Will increment a counter relay denied
 */
public class MetricsMailet extends GenericMailet {
    public static final String METRIC_NAME = "metricName";

    private final MetricFactory metricFactory;
    private Metric metric;

    @Inject
    public MetricsMailet(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void init() throws MessagingException {
        String metricName = getInitParameter(METRIC_NAME);
        init(metricName);
    }

    private void init(String metricName) {
        Preconditions.checkNotNull(metricName);
        metric = metricFactory.generate(metricName);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        metric.increment();
    }

    @Override
    public String getMailetInfo() {
        return "Metrics mailet";
    }
}
