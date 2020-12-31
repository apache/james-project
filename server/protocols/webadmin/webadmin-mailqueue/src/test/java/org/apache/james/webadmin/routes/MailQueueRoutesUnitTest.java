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

package org.apache.james.webadmin.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailQueueRoutesUnitTest {
    MailQueueRoutes testee;

    @BeforeEach
    void setup() {
        MemoryTaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        MailQueueFactory<ManageableMailQueue> mailQueueFactory = null;
        testee = new MailQueueRoutes(mailQueueFactory, new JsonTransformer(), taskManager);
    }

    @Test
    void isDelayedShouldReturnEmptyWhenNull() {
        Optional<Boolean> delayed = testee.isDelayed(null);
        assertThat(delayed).isEmpty();
    }

    @Test
    void isDelayedShouldBeEqualsToTrueWhenTrue() {
        Optional<Boolean> delayed = testee.isDelayed("true");
        assertThat(delayed).contains(true);
    }

    @Test
    void isDelayedShouldBeEqualsToFalseWhenFalse() {
        Optional<Boolean> delayed = testee.isDelayed("false");
        assertThat(delayed).contains(false);
    }

    @Test
    void isDelayedShouldBeEqualsToFalseWhenOtherValue() {
        Optional<Boolean> delayed = testee.isDelayed("abc");
        assertThat(delayed).contains(false);
    }
}
