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

import java.io.IOException;
import java.util.Properties;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;


public class LocalKafka {

    private static Properties getPropertiesFromFile(String name) throws IOException {
        Properties zkProperties = new Properties();
        zkProperties.load(Class.class.getResourceAsStream(name));
        return zkProperties;
    }

    public final KafkaServerStartable kafka;
    public final LocalZooKeeper zookeeper;

    public LocalKafka() throws IOException, InterruptedException {
        this(getPropertiesFromFile("/kafka.properties"), getPropertiesFromFile("/zookeeper.properties"));
    }

    public LocalKafka(Properties kafkaProperties, Properties zkProperties) throws IOException, InterruptedException {
        KafkaConfig kafkaConfig = new KafkaConfig(kafkaProperties);

        //start local zookeeper
        zookeeper = new LocalZooKeeper(zkProperties);

        //start local kafka broker
        kafka = new KafkaServerStartable(kafkaConfig);
        kafka.startup();
    }

    public void stop(){
        kafka.shutdown();
    }

}
