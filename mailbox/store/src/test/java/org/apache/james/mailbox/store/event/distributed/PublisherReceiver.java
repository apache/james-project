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

package org.apache.james.mailbox.store.event.distributed;

import org.apache.james.mailbox.store.publisher.MessageConsumer;
import org.apache.james.mailbox.store.publisher.MessageReceiver;
import org.apache.james.mailbox.store.publisher.Publisher;
import org.apache.james.mailbox.store.publisher.Topic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class PublisherReceiver implements Publisher, MessageConsumer {

    private final Multimap<Topic, MessageReceiver> messageReceiverMultimap;
    // Test code is mutable. Agree, this is not nice, but quite convenient . MessageConsumer is designed to handle only one message receiver.
    // Here we want to emulate a complete event systems, across multiple servers...
    private MessageReceiver messageReceiver;

    public PublisherReceiver() {
        this.messageReceiverMultimap = HashMultimap.create();
    }

    @Override
    public void close() {

    }

    @Override
    public void publish(Topic topic, byte[] message) {
        for (MessageReceiver messageReceiver : messageReceiverMultimap.get(topic)) {
            messageReceiver.receiveSerializedEvent(message);
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void setMessageReceiver(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    @Override
    public void init(Topic topic) throws Exception {
        messageReceiverMultimap.put(topic, messageReceiver);
    }

    @Override
    public void destroy() throws Exception {

    }
}
