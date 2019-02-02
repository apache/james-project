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

package org.apache.james.queue.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MailQueueNameTest {

    @Test
    void fromStringShouldThrowWhenNull() {
        assertThatThrownBy(() -> MailQueueName.fromString(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromStringShouldReturnInstanceWhenEmptyString() {
        assertThat(MailQueueName.fromString("")).isNotNull();
    }

    @Test
    void fromStringShouldReturnInstanceWhenArbitraryString() {
        assertThat(MailQueueName.fromString("whatever")).isNotNull();
    }

    @Test
    void fromRabbitWorkQueueNameShouldThrowWhenNull() {
        assertThatThrownBy(() -> MailQueueName.fromRabbitWorkQueueName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnEmptyWhenArbitraryString() {
        assertThat(MailQueueName.fromRabbitWorkQueueName("whatever"))
            .isEmpty();
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnInstanceWhenPrefixOnlyString() {
        assertThat(MailQueueName.fromRabbitWorkQueueName(MailQueueName.WORKQUEUE_PREFIX))
            .contains(MailQueueName.fromString(""));
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnInstanceWhenValidQueueName() {
        assertThat(MailQueueName.fromRabbitWorkQueueName(MailQueueName.WORKQUEUE_PREFIX + "myQueue"))
            .contains(MailQueueName.fromString("myQueue"));
    }

    @Test
    void shouldConformToBeanContract() {
        EqualsVerifier.forClass(MailQueueName.class).verify();
    }

    @Test
    void exchangeNameShouldConformToBeanContract() {
        EqualsVerifier.forClass(MailQueueName.ExchangeName.class).verify();
    }

    @Test
    void workQueueNameShouldConformToBeanContract() {
        EqualsVerifier.forClass(MailQueueName.WorkQueueName.class).verify();
    }

    @Test
    void fromRabbitWorkQueueNameShouldReturnIdentityWhenToRabbitWorkQueueName() {
        MailQueueName myQueue = MailQueueName.fromString("myQueue");
        assertThat(MailQueueName.fromRabbitWorkQueueName(myQueue.toWorkQueueName().asString()))
            .contains(myQueue);
    }

}