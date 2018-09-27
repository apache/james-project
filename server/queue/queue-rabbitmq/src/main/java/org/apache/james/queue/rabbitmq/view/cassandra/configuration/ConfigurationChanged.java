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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;

import com.google.common.base.Preconditions;

class ConfigurationChanged implements Event {

    private final EventId eventId;
    private final AggregateId aggregateId;
    private final CassandraMailQueueViewConfiguration configuration;

    ConfigurationChanged(AggregateId aggregateId,
                         EventId eventId,
                         CassandraMailQueueViewConfiguration configuration) {
        Preconditions.checkNotNull(aggregateId);
        Preconditions.checkNotNull(eventId);
        Preconditions.checkNotNull(configuration);

        this.aggregateId = aggregateId;
        this.eventId = eventId;
        this.configuration = configuration;
    }

    CassandraMailQueueViewConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public AggregateId getAggregateId() {
        return aggregateId;
    }
}
