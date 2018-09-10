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
package org.apache.james.backend.mailqueue;

import java.util.Optional;
import java.util.UUID;

import org.apache.james.util.docker.Images;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.ConnectionFactory;

public class DockerRabbitMQ {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRabbitMQ.class);

    private static final String DEFAULT_RABBIT_NODE = "my-rabbit";
    private static final int DEFAULT_RABBITMQ_PORT = 5672;
    private static final int DEFAULT_RABBITMQ_ADMIN_PORT = 15672;
    private static final String DEFAULT_RABBITMQ_USERNAME = "guest";
    private static final String DEFAULT_RABBITMQ_PASSWORD = "guest";
    private static final String RABBITMQ_ERLANG_COOKIE = "RABBITMQ_ERLANG_COOKIE";
    private static final String RABBITMQ_NODENAME = "RABBITMQ_NODENAME";

    private final GenericContainer<?> container;
    private final Optional<String> nodeName;

    public static DockerRabbitMQ withCookieAndNodeName(String hostName, String erlangCookie, String nodeName, Network network) {
        return new DockerRabbitMQ(Optional.ofNullable(hostName), Optional.ofNullable(erlangCookie), Optional.ofNullable(nodeName),
            Optional.of(network));
    }

    public static DockerRabbitMQ withoutCookie() {
        return new DockerRabbitMQ(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @SuppressWarnings("resource")
    private DockerRabbitMQ(Optional<String> hostName, Optional<String> erlangCookie, Optional<String> nodeName, Optional<Network> net) {
        container = new GenericContainer<>(Images.RABBITMQ)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(hostName.orElse(randomName())))
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName(hostName.orElse(DEFAULT_RABBIT_NODE)))
                .withExposedPorts(DEFAULT_RABBITMQ_PORT, DEFAULT_RABBITMQ_ADMIN_PORT)
                .waitingFor(new WaitAllStrategy()
                    .withStrategy(Wait.forHttp("").forPort(DEFAULT_RABBITMQ_ADMIN_PORT))
                    .withStrategy(RabbitMQWaitStrategy.withDefaultTimeout(this)))
                .withLogConsumer(frame -> LOGGER.debug(frame.getUtf8String()))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                    .withTmpFs(ImmutableMap.of("/var/lib/rabbitmq/mnesia", "rw,noexec,nosuid,size=100m")));
        net.ifPresent(container::withNetwork);
        erlangCookie.ifPresent(cookie -> container.withEnv(RABBITMQ_ERLANG_COOKIE, cookie));
        nodeName.ifPresent(name -> container.withEnv(RABBITMQ_NODENAME, name));
        this.nodeName = nodeName;
    }

    private String randomName() {
        return UUID.randomUUID().toString();
    }

    public String getHostIp() {
        return container.getContainerIpAddress();
    }

    public Integer getPort() {
        return container.getMappedPort(DEFAULT_RABBITMQ_PORT);
    }

    public Integer getAdminPort() {
        return container.getMappedPort(DEFAULT_RABBITMQ_ADMIN_PORT);
    }

    public String getUsername() {
        return DEFAULT_RABBITMQ_USERNAME;
    }

    public String getPassword() {
        return DEFAULT_RABBITMQ_PASSWORD;
    }

    public ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(getHostIp());
        connectionFactory.setPort(getPort());
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword());
        return connectionFactory;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void restart() {
        DockerClientFactory.instance().client()
            .restartContainerCmd(container.getContainerId());
    }

    public GenericContainer<?> container() {
        return container;
    }

    public String node() {
        return nodeName.get();
    }

    public void join(DockerRabbitMQ rabbitMQ) throws Exception {
        stopApp();
        joinCluster(rabbitMQ);
    }

    private void stopApp() throws java.io.IOException, InterruptedException {
        String stdout = container()
            .execInContainer("rabbitmqctl", "stop_app")
            .getStdout();
        LOGGER.debug("stop_app: {}", stdout);
    }

    private void joinCluster(DockerRabbitMQ rabbitMQ) throws java.io.IOException, InterruptedException {
        String stdout = container()
            .execInContainer("rabbitmqctl", "join_cluster", rabbitMQ.node())
            .getStdout();
        LOGGER.debug("join_cluster: {}", stdout);
    }

    public void startApp() throws Exception {
        String stdout = container()
                .execInContainer("rabbitmqctl", "start_app")
                .getStdout();
        LOGGER.debug("start_app: {}", stdout);
    }

    public void reset() throws Exception {
        stopApp();

        String stdout = container()
            .execInContainer("rabbitmqctl", "reset")
            .getStdout();
        LOGGER.debug("reset: {}", stdout);

        startApp();
    }
}
