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

package org.apache.james.queue.jms;

import org.apache.james.queue.api.MailQueue;

/**
 * Provides additional options when creating consumers.
 */
public interface ConsumerOptions {
    /**
     * The empty consumer options.
     */
    ConsumerOptions EMPTY = queueName -> queueName;

    /**
     * The queue name with options for performing {@link MailQueue#deQueue()} operation. The implementation may
     * return {@code queueName} itself if it does not support any options.
     *
     * @return The queue name maybe with additional options.
     */
    String applyForDequeue(String queueName);
}
