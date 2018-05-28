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

import org.apache.commons.text.RandomStringGenerator;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.RemoveVolumeCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Binds;
import com.github.dockerjava.api.model.Volume;


public class DockerCassandraRule implements TestRule {

    private static final Logger logger = LoggerFactory.getLogger(DockerCassandraRule.class);

    private static final int CASSANDRA_PORT = 9042;
    private static final String CASSANDRA_CONFIG_DIR = "$CASSANDRA_CONFIG";
    private static final String CASSANDRA_YAML = CASSANDRA_CONFIG_DIR + "/cassandra.yaml";
    private static final String CASSANDRA_ENV = CASSANDRA_CONFIG_DIR + "/cassandra-env.sh";
    private static final String JVM_OPTIONS = CASSANDRA_CONFIG_DIR + "/jvm.options";

    private final GenericContainer<?> cassandraContainer;
    private final DockerClient client;
    private final CreateVolumeCmd createTmpsFsCmd;
    private final RemoveVolumeCmd deleteTmpsFsCmd;

    @SuppressWarnings("resource")
    public DockerCassandraRule() {
        String tmpFsName = "cassandraData" + new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10);
        client = DockerClientFactory.instance().client();
        createTmpsFsCmd = client.createVolumeCmd()
            .withName(tmpFsName)
            .withDriver("local")
            .withDriverOpts(
                ImmutableMap.of(
                    "type", "tmpfs",
                    "device", "tmpfs",
                    "o", "size=100m"));
        deleteTmpsFsCmd = client.removeVolumeCmd(tmpFsName);
        boolean deleteOnExit = false;
        cassandraContainer = new GenericContainer<>(
            new ImageFromDockerfile("cassandra_2_2_10", deleteOnExit)
                .withDockerfileFromBuilder(builder ->
                    builder
                        .from("cassandra:2.2.10")
                        .env("ENV CASSANDRA_CONFIG", "/etc/cassandra")
                        //avoiding token range computation helps starting faster
                        .run("echo \"JVM_OPTS=\\\"\\$JVM_OPTS -Dcassandra.initial_token=0\\\"\" >> " + CASSANDRA_ENV)
                        .run("sed -i -e \"s/num_tokens/\\#num_tokens/\" " + CASSANDRA_YAML)
                        //don't wait for other nodes communication to happen
                        .run("echo \"JVM_OPTS=\\\"\\$JVM_OPTS -Dcassandra.skip_wait_for_gossip_to_settle=0\\\"\" >> " + CASSANDRA_ENV)
                        //make sure commit log disk flush won't happen
                        .run("sed -i -e \"s/commitlog_sync_period_in_ms: 10000/commitlog_sync_period_in_ms: 9999999/\" " + CASSANDRA_YAML)
                        //auto_bootstrap should be useless when no existing data
                        .run("echo auto_bootstrap: false >> " + CASSANDRA_YAML)
                        .run("echo \"-Xms1500M\" >> " + JVM_OPTIONS)
                        .run("echo \"-Xmx1500M\" >> " + JVM_OPTIONS)
                        .build()))
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withBinds(new Binds(new Bind(tmpFsName, new Volume("/var/lib/cassandra")))))
            .withCreateContainerCmdModifier(cmd -> cmd.withMemory(2000 * 1024 * 1024L))
            .withExposedPorts(CASSANDRA_PORT)
            .withLogConsumer(this::displayDockerLog);
        cassandraContainer
            .waitingFor(new CassandraWaitStrategy(cassandraContainer));
    }

    private void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String());
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    createTmpsFsCmd.exec();
                    cassandraContainer.apply(base, description).evaluate();
                } finally {
                    deleteTmpsFsCmd.exec();
                }
            }
        };
    }

    public void start() {
        createTmpsFsCmd.exec();
        cassandraContainer.start();
    }

    public void stop() {
        try {
            cassandraContainer.stop();
        } finally {
            deleteTmpsFsCmd.exec();
        }
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
