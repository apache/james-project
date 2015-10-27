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

package org.apache.james.mailbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.mailbox.kafka.KafkaMessageConsumer;
import org.apache.james.mailbox.kafka.KafkaPublisher;
import org.apache.james.mailbox.store.publisher.MessageReceiver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KafkaMessagingTest {

    public static final String TOPIC = "TOPIC";
    public static final byte[] MESSAGE = new byte[10];

    private LocalKafka kafka;
    private KafkaPublisher kafkaPublisher;
    private MessageReceiver messageReceiver;

    @Before
    public void setUp() throws Exception {
        kafka = new LocalKafka();
        Thread.sleep(5000);
        kafkaPublisher = new KafkaPublisher("127.0.0.1", 9092);
        kafkaPublisher.init();
        messageReceiver = mock(MessageReceiver.class);
        KafkaMessageConsumer kafkaMessageConsumer = new KafkaMessageConsumer("localhost", "0123456789", 2);
        kafkaMessageConsumer.setMessageReceiver(messageReceiver);
        kafkaMessageConsumer.init(TOPIC);
        Thread.sleep(5000);
    }

    @After
    public void tearDown() throws Exception {
        kafka.stop();
    }

    @Test
    public void testSomething() throws Exception {
        kafkaPublisher.publish(TOPIC, MESSAGE);
        verify(messageReceiver).receiveSerializedEvent(MESSAGE);
        verifyNoMoreInteractions(messageReceiver);
    }

}
