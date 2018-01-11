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

package org.apache.james.queue.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MemoryMailQueueFactoryTest {

    private static final String KEY = "key";
    private static final String BIS = "keyBis";

    private MemoryMailQueueFactory memoryMailQueueFactory;

    @BeforeEach
    public void setUp() {
        memoryMailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
    }

    @Test
    public void getQueueShouldNotReturnNull() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isNotNull();
    }

    @Test
    public void getQueueShouldReturnTwoTimeTheSameResultWhenUsedWithTheSameKey() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isEqualTo(memoryMailQueueFactory.getQueue(KEY));
    }

    @Test
    public void getQueueShouldNotReturnTheSameQueueForTwoDifferentNames() {
        assertThat(memoryMailQueueFactory.getQueue(KEY)).isNotEqualTo(memoryMailQueueFactory.getQueue(BIS));
    }
}
