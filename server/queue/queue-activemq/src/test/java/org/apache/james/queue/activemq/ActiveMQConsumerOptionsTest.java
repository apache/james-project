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

package org.apache.james.queue.activemq;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActiveMQConsumerOptionsTest {
    private String queueName;

    @BeforeEach
    void setUp() {
        // append schema to use URL assertions.
        queueName = "http://test_queue";
    }

    @Test
    void emptyConsumerOptionsShouldNotHaveAnQuery() throws MalformedURLException {
        ActiveMQConsumerOptions options = ActiveMQConsumerOptions.builder().build();

        assertThat(new URL(options.applyForDequeue(queueName)))
                .hasNoQuery();
    }

    @Test
    void consumerOptionsWithAllOptionsShouldHaveAllParameters() throws MalformedURLException {
        ActiveMQConsumerOptions options = ActiveMQConsumerOptions.builder()
                .prefetchSize(100)
                .dispatchAsync(true)
                .maximumPendingMessageLimit(10)
                .exclusive(true)
                .noLocal(false)
                .retroactive(false)
                .selector("selector")
                .priority(512)
                .build();

        assertThat(new URL(options.applyForDequeue(queueName)))
                .hasParameter(ActiveMQSupport.CONSUMER_PREFETCH_SIZE, "100")
                .hasParameter(ActiveMQSupport.CONSUMER_DISPATCH_ASYNC, "true")
                .hasParameter(ActiveMQSupport.CONSUMER_MAXIMUM_PENDING_MESSAGE_LIMIT, "10")
                .hasParameter(ActiveMQSupport.CONSUMER_EXCLUSIVE, "true")
                .hasParameter(ActiveMQSupport.CONSUMER_NO_LOCAL, "false")
                .hasParameter(ActiveMQSupport.CONSUMER_RETROACTIVE, "false")
                .hasParameter(ActiveMQSupport.CONSUMER_SELECTOR, "selector")
                .hasParameter(ActiveMQSupport.CONSUMER_PRIORITY, "512");
    }

    @Test
    void consumerOptionsWithSomeOptionsMissingShouldNotBeIncluded() throws MalformedURLException {
        ActiveMQConsumerOptions options = ActiveMQConsumerOptions.builder().priority(-1).build();

        assertThat(new URL(options.applyForDequeue(queueName)))
                .hasQuery(ActiveMQSupport.CONSUMER_PRIORITY + "=-1");
    }

    @Test
    void consumerOptionsShouldReturnArgWhenNoOptionsAreGiven() {
        ActiveMQConsumerOptions options = ActiveMQConsumerOptions.builder().build();

        assertThat(options.applyForDequeue(queueName)).isSameAs(queueName);
    }
}