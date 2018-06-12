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

package org.apache.james.mailbox.store.event;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynchronousEventDelivery implements EventDelivery {

    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronousEventDelivery.class);

    private final MetricFactory metricFactory;

    @Inject
    public SynchronousEventDelivery(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void deliver(MailboxListener mailboxListener, Event event) {
        TimeMetric timer = metricFactory.timer("mailbox-listener-" + mailboxListener.getClass().getSimpleName());
        try {
            mailboxListener.event(event);
        } catch (Throwable throwable) {
            LOGGER.error("Error while processing listener {} for {}",
                    mailboxListener.getClass().getCanonicalName(), event.getClass().getCanonicalName(),
                    throwable);
        } finally {
            timer.stopAndPublish();
        }
    }
}
