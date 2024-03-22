/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.queue.rabbitmq.view.cassandra;

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement;

import com.google.common.annotations.VisibleForTesting;

public class CassandraMailQueueViewStartUpCheck implements StartUpCheck {
    private static final String NAME = "cassandra-mail-queue-view-startup-check";
    private final EventsourcingConfigurationManagement eventsourcingConfigurationManagement;
    private final CassandraMailQueueViewConfiguration configuration;

    @VisibleForTesting
    @Inject
    public CassandraMailQueueViewStartUpCheck(EventsourcingConfigurationManagement eventsourcingConfigurationManagement,
                                       CassandraMailQueueViewConfiguration configuration) {
        this.eventsourcingConfigurationManagement = eventsourcingConfigurationManagement;
        this.configuration = configuration;
    }

    @Override
    public CheckResult check() {
        try {
            eventsourcingConfigurationManagement.registerConfiguration(configuration);
            return CheckResult.builder()
                .checkName(NAME)
                .resultType(ResultType.GOOD)
                .build();
        } catch (IllegalArgumentException e) {
            return CheckResult.builder()
                .checkName(NAME)
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    @Override
    public String checkName() {
        return NAME;
    }
}
