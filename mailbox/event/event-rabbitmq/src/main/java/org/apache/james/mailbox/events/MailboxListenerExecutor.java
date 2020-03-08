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

package org.apache.james.mailbox.events;

import static org.apache.james.mailbox.events.EventBus.Metrics.timerName;

import java.io.Closeable;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;

public class MailboxListenerExecutor {
    private final MetricFactory metricFactory;

    public MailboxListenerExecutor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    void execute(MailboxListener listener, MDCBuilder mdcBuilder, Event event) throws Exception {
        if (listener.isHandling(event)) {
            try (Closeable mdc = buildMDC(listener, mdcBuilder, event)) {
                TimeMetric timer = metricFactory.timer(timerName(listener));
                try {
                    listener.event(event);
                } finally {
                    timer.stopAndPublish();
                }
            }
        }
    }

    private Closeable buildMDC(MailboxListener listener, MDCBuilder mdcBuilder, Event event) {
        return mdcBuilder
            .addContext(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId())
            .addContext(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass())
            .addContext(EventBus.StructuredLoggingFields.USER, event.getUsername())
            .addContext(EventBus.StructuredLoggingFields.LISTENER_CLASS, listener.getClass())
            .build();
    }
}
