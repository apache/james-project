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

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.apache.james.mailbox.store.publisher.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Properties;

public class KafkaPublisher implements Publisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPublisher.class);

    private Producer<String, byte[]> producer;
    private final int kafka_port;
    private final String kafka_ip;
    private boolean producerLaunched;

    public KafkaPublisher(String kafkaHostIpString, int kafka_port) {
        this.kafka_ip = kafkaHostIpString;
        this.kafka_port = kafka_port;
        producerLaunched = false;
    }

    @PostConstruct
    @Override
    public void init() {
        if (!producerLaunched) {
            Properties props = new Properties();
            props.put("metadata.broker.list", kafka_ip + ":" + kafka_port);
            props.put("serializer.class", "kafka.serializer.DefaultEncoder");
            props.put("request.required.acks", "1");
            ProducerConfig config = new ProducerConfig(props);
            producer = new Producer<>(config);
            producerLaunched = true;
        } else {
            LOG.warn("Kafka producer was already instantiated");
        }
    }


    @Override
    public void publish(String topic, byte[] message) {
        producer.send(new KeyedMessage<>(topic, message));
    }

    @PreDestroy
    @Override
    public void close() {
        producer.close();
    }

}
