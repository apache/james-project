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

public interface MailQueueFactoryContract {

    String NAME_1 = "name1";
    String NAME_2 = "name2";

    MailQueueFactory getMailQueueFactory();

    @Test
    default void getUsedMailQueuesShouldReturnWhenNoMailQueue() {
        assertThat(getMailQueueFactory().getUsedMailQueues())
            .isEmpty();
    }

    @Test
    default void getUsedMailQueuesShouldReturnPreviouslyCreatedMailQueues() {
        MailQueueFactory mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.getQueue(NAME_1);
        mailQueueFactory.getQueue(NAME_2);

        assertThat(mailQueueFactory.getUsedMailQueues())
            .extracting(MailQueue::getMailQueueName)
            .containsOnly(NAME_1, NAME_2);
    }

    @Test
    default void getUsedMailQueuesShouldNotReturnDuplicate() {
        MailQueueFactory mailQueueFactory = getMailQueueFactory();

        mailQueueFactory.getQueue(NAME_1);
        mailQueueFactory.getQueue(NAME_1);

        assertThat(mailQueueFactory.getUsedMailQueues())
            .extracting(MailQueue::getMailQueueName)
            .containsOnly(NAME_1);
    }

}