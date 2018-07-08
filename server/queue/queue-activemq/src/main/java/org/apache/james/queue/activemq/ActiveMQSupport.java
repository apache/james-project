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

import org.apache.james.queue.jms.JMSSupport;

/**
 * Interface which should get implemented by ActiveMQ depending implementions
 */
public interface ActiveMQSupport extends JMSSupport {

    /**
     * The name of the Queue the mail is stored in
     */
    String JAMES_QUEUE_NAME = "JAMES_QUEUE_NAME";

    /**
     * The URL of the Blobmessage content
     */
    String JAMES_BLOB_URL = "JAMES_BLOB_URL";

    /**
     * Indicate that the Blobmessage content is reused for a new message
     */
    String JAMES_REUSE_BLOB_URL = " JAMES_REUSE_BLOB_URL";

    /**
     * Should the broker dispatch messages asynchronously to the consumer.
     */
    String CONSUMER_DISPATCH_ASYNC = "consumer.dispatchAsync";

    /**
     * The broker will pick a single MessageConsumer to get all the messages for a queue to ensure ordering.
     */
    String CONSUMER_EXCLUSIVE = "consumer.exclusive";

    /**
     * Use to control if messages for non-durable topics are dropped if a slow consumer situation exists.
     */
    String CONSUMER_MAXIMUM_PENDING_MESSAGE_LIMIT = "consumer.maximumPendingMessageLimit";

    /**
     * Same as the noLocal flag on a Topic consumer. Exposed here so that it can be used with a queue.
     */
    String CONSUMER_NO_LOCAL = "consumer.noLocal";

    /**
     * The number of message the consumer will prefetch.
     */
    String CONSUMER_PREFETCH_SIZE = "consumer.prefetchSize";

    /**
     * This allows us to weight consumers to optimize network hops.
     */
    String CONSUMER_PRIORITY = "consumer.priority";

    /**
     * A retroactive consumer is just a regular JMS Topic consumer who indicates that at the start of a subscription
     * every attempt should be used to go back in time and send any old messages (or the last message sent on that
     * topic) that the consumer may have missed.
     */
    String CONSUMER_RETROACTIVE = "consumer.retroactive";

    /**
     * JMS Selector used with the consumer.
     */
    String CONSUMER_SELECTOR = "consumer.selector";
}
