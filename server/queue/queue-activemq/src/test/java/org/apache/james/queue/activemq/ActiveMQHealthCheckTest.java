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

import static org.apache.james.queue.activemq.ActiveMQHealthCheck.COMPONENT_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.queue.jms.BrokerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import reactor.core.publisher.Mono;

@Tag(BrokerExtension.STATISTICS)
@ExtendWith(BrokerExtension.class)
class ActiveMQHealthCheckTest {

    private ActiveMQHealthCheck testee;
    private BrokerService broker;

    @BeforeEach
    void setup(BrokerService broker) {
        this.broker = broker;
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?create=false");
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(0);
        connectionFactory.setPrefetchPolicy(prefetchPolicy);

        testee = new ActiveMQHealthCheck(connectionFactory, new NoopGaugeRegistry());
    }

    @Test
    void componentNameShouldReturnTheRightValue() {
        assertThat(testee.componentName().getName())
            .isEqualTo(COMPONENT_NAME.getName());
    }

    @Test
    void checkShouldReturnHealthyWhenActiveMQHealthy() {
        assertThat(Mono.from(testee.check()).block())
            .isEqualTo(Result.healthy(COMPONENT_NAME));
    }

    @Test
    void checkShouldReturnUnHealthyWhenActiveMQDown() throws Exception {
        broker.stop();
        assertThat(Mono.from(testee.check()).block().isUnHealthy()).isTrue();
    }
}

