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
package org.apache.james.queue.api.mock;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;

public class MockMailQueueFactory implements MailQueueFactory {

    private final Map<String, MailQueue> queues = new HashMap<String, MailQueue>();

    @Override
    public synchronized MailQueue getQueue(String name) {
        MailQueue queue = queues.get(name);
        if (queue == null) {
            queue = new MockMailQueue();
            queues.put(name, queue);
        }

        return queue;
    }

    public void clear() {
        queues.clear();
    }
}
