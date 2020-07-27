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

package org.apache.james.backends.rabbitmq;

import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;

public interface RabbitMQFixture {
    String EXCHANGE_NAME = "exchangeName";
    String ROUTING_KEY = "routingKey";
    String WORK_QUEUE = "workQueue";
    String WORK_QUEUE_SINGLE_ACTIVE_CONSUMER = "workQueueSingleActiveConsumer";
    String WORK_QUEUE_2 = "workQueue2";

    String DEFAULT_USER = "guest";
    String DEFAULT_PASSWORD_STRING = "guest";
    char[] DEFAULT_PASSWORD = DEFAULT_PASSWORD_STRING.toCharArray();
    RabbitMQConfiguration.ManagementCredentials DEFAULT_MANAGEMENT_CREDENTIAL = new RabbitMQConfiguration.ManagementCredentials(DEFAULT_USER, DEFAULT_PASSWORD);

    Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    Duration THIRTY_SECONDS = new Duration(30, TimeUnit.SECONDS);
    ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    ConditionFactory awaitAtMostOneMinute = calmlyAwait.atMost(ONE_MINUTE);
    ConditionFactory awaitAtMostThirtySeconds = calmlyAwait.atMost(THIRTY_SECONDS);
}
