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

package org.apache.james.mailbox.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.james.mailbox.store.publisher.MessageConsumer;
import org.apache.james.mailbox.store.publisher.MessageReceiver;
import org.apache.james.mailbox.store.publisher.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;

public class KafkaMessageConsumer implements MessageConsumer {

    private class Consumer implements Runnable {

        private final KafkaStream<byte[], byte[]> stream;

        public Consumer(KafkaStream<byte[], byte[]> stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            for (MessageAndMetadata<byte[], byte[]> message : stream) {
                messageReceiver.receiveSerializedEvent(message.message());
            }
        }
    }

    private static final String ZK_SESSION_TIMEOUT = "400";
    private static final String ZK_SYNC_TIME = "200";
    private static final String AUTO_COMMIT8INTERVAL_MS = "1000";
    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    private final ConsumerConnector consumer;
    private final int numberOfTread;
    private MessageReceiver messageReceiver;
    private ExecutorService executor;
    private boolean isInitialized;


    public KafkaMessageConsumer(String zookeeperConnectionString,
                                String groupId,
                                int numberOfThread) {
        this.consumer = kafka.consumer.Consumer.createJavaConsumerConnector(createConsumerConfig(zookeeperConnectionString, groupId));
        this.numberOfTread = numberOfThread;
        this.isInitialized = false;
    }

    @Override
    public void setMessageReceiver(MessageReceiver messageReceiver) {
        if (!isInitialized) {
            this.messageReceiver = messageReceiver;
        } else {
            throw new RuntimeException("Can not change the MessageReceiver of a running KafkaMessageConsumer");
        }
    }

    @Override
    @PreDestroy
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
        if (executor != null) {
            executor.shutdown();
        }
        this.isInitialized = false;
    }

    @Override
    @PostConstruct
    public void init(Topic topic) {
        if (!isInitialized) {
            this.isInitialized = true;
            List<KafkaStream<byte[], byte[]>> streams = getKafkaStreams(topic.getValue());
            executor = Executors.newFixedThreadPool(numberOfTread);
            startConsuming(streams);
        } else {
            LOG.warn("This Kafka MailboxMessage Receiver was already launched.");
        }
    }

    private List<KafkaStream<byte[], byte[]>> getKafkaStreams(String topic) {
        Map<String, Integer> topicCountMap = new HashMap<>();
        topicCountMap.put(topic, numberOfTread);
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        return consumerMap.get(topic);
    }

    private void startConsuming(List<KafkaStream<byte[], byte[]>> streams) {
        streams.forEach(stream -> executor.submit(new Consumer(stream)));
    }

    private ConsumerConfig createConsumerConfig(String zookeeperConnectionString, String groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", zookeeperConnectionString);
        props.put("group.id", groupId);
        props.put("zookeeper.session.timeout.ms", ZK_SESSION_TIMEOUT);
        props.put("zookeeper.sync.time.ms", ZK_SYNC_TIME);
        props.put("auto.commit.interval.ms", AUTO_COMMIT8INTERVAL_MS);
        return new ConsumerConfig(props);
    }

}