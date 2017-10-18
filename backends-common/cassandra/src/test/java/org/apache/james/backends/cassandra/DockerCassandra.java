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

package org.apache.james.backends.cassandra;

import org.apache.james.util.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.dockerjava.api.DockerClient;

public class DockerCassandra {

    private static final Logger logger = LoggerFactory.getLogger(DockerCassandra.class);

    private static final int CASSANDRA_PORT = 9042;
    private static final String CASSANDRA_CONFIG_DIR = "$CASSANDRA_CONFIG";
    private static final String CASSANDRA_YAML = CASSANDRA_CONFIG_DIR + "/cassandra.yaml";
    private static final String CASSANDRA_ENV = CASSANDRA_CONFIG_DIR + "/cassandra-env.sh";
    private static final String JVM_OPTIONS = CASSANDRA_CONFIG_DIR + "/jvm.options";

    private final GenericContainer<?> cassandraContainer;
    private final DockerClient client;

    @SuppressWarnings("resource")
    public DockerCassandra() {
        client = DockerClientFactory.instance().client();
        boolean deleteOnExit = false;
        int cassandraMemory = 1000;
        long cassandraContainerMemory = Float.valueOf(cassandraMemory * 1.2f * 1024 * 1024L).longValue();
        cassandraContainer = new GenericContainer<>(
            new ImageFromDockerfile("cassandra_3_11_1", deleteOnExit)
                .withDockerfileFromBuilder(builder ->
                    builder
                        .from("cassandra:3.11.1")
                        .env("ENV CASSANDRA_CONFIG", "/etc/cassandra")
                        .run("echo \"-Xms" + cassandraMemory + "M\" >> " + JVM_OPTIONS)
                        .run("echo \"-Xmx" + cassandraMemory + "M\" >> " + JVM_OPTIONS)
                        .build()))
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withTmpFs(ImmutableMap.of("/var/lib/cassandra", "rw,noexec,nosuid,size=200m")))
            .withCreateContainerCmdModifier(cmd -> cmd.withMemory(cassandraContainerMemory))
            .withExposedPorts(CASSANDRA_PORT)
            .withLogConsumer(DockerCassandra::displayDockerLog);
        cassandraContainer
            .waitingFor(new CassandraWaitStrategy(cassandraContainer));
    }

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String());
    }

    public void start() {
        cassandraContainer.start();
    }

    public void stop() {
        cassandraContainer.stop();
    }

    public Host getHost() {
        return Host.from(
            getIp(),
            getBindingPort());
    }
    
    public String getIp() {
        return cassandraContainer.getContainerIpAddress();
    }

    public int getBindingPort() {
        return cassandraContainer.getMappedPort(CASSANDRA_PORT);
    }

    public GenericContainer<?> getRawContainer() {
        return cassandraContainer;
    }

    public void pause() {
        client.pauseContainerCmd(cassandraContainer.getContainerId());
    }

    public void unpause() {
        client.unpauseContainerCmd(cassandraContainer.getContainerId());
    }

}
