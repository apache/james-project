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

package org.apache.james.queue.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public interface MailQueueFactoryContract<T extends MailQueue> {

    MailQueueName NAME_1 = MailQueueName.of("name1");
    MailQueueName NAME_2 = MailQueueName.of("name2");

    MailQueueFactory<T> getMailQueueFactory();

    @Test
    default void getUsedMailQueuesShouldReturnWhenNoMailQueue() {
        assertThat(getMailQueueFactory().listCreatedMailQueues())
            .isEmpty();
    }

    @Test
    default void getUsedMailQueuesShouldReturnPreviouslyCreatedMailQueues() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.createQueue(NAME_1);
        mailQueueFactory.createQueue(NAME_2);

        assertThat(mailQueueFactory.listCreatedMailQueues()).containsOnly(NAME_1, NAME_2);
    }

    @Test
    default void getUsedMailQueuesShouldNotReturnDuplicate() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.createQueue(NAME_1);
        mailQueueFactory.createQueue(NAME_1);

        assertThat(mailQueueFactory.listCreatedMailQueues())
            .containsOnly(NAME_1);
    }

    @Test
    default void getMailQueueShouldReturnEmptyIfNotCreated() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        assertThat(mailQueueFactory.getQueue(NAME_1)).isEmpty();
    }

    @Test
    default void getMailQueueShouldReturnMailQueueIfCreated() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.createQueue(NAME_1);

        assertThat(mailQueueFactory.getQueue(NAME_1).map(MailQueue::getName)).contains(NAME_1);
    }

    @Test
    default void getMailQueueShouldReturnEmptyIfQueueDoesNotExist() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.createQueue(NAME_1);

        assertThat(mailQueueFactory.getQueue(NAME_2)).isEmpty();
    }

    @Test
    default void getMailQueueShouldNotReturnTheSameQueueForTwoDifferentNames() {
        MailQueueFactory<T> mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.createQueue(NAME_1);
        mailQueueFactory.createQueue(NAME_2);

        assertThat(mailQueueFactory.getQueue(NAME_1)).isNotEqualTo(mailQueueFactory.getQueue(NAME_2));
    }


}