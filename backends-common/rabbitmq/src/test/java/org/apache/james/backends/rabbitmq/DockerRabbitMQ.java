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
package org.apache.james.backends.rabbitmq;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;

public class DockerRabbitMQ {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRabbitMQ.class);

    private static final int MAX_THREE_RETRIES = 3;
    private static final int MIN_DELAY_OF_TEN_MILLISECONDS = 10;
    private static final int CONNECTION_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int CHANNEL_RPC_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int HANDSHAKE_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int SHUTDOWN_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final int NETWORK_RECOVERY_INTERVAL_OF_ONE_HUNDRED_MILLISECOND = 100;
    private static final String DEFAULT_RABBIT_HOST_NAME_PREFIX = "my-rabbit";
    private static final String DEFAULT_RABBIT_NODE_NAME_PREFIX = "rabbit";
    private static final int DEFAULT_RABBITMQ_PORT = 5672;
    private static final int DEFAULT_RABBITMQ_ADMIN_PORT = 15672;
    private static final String DEFAULT_RABBITMQ_USERNAME = "guest";
    private static final String DEFAULT_RABBITMQ_PASSWORD = "guest";
    private static final String RABBITMQ_ERLANG_COOKIE = "RABBITMQ_ERLANG_COOKIE";
    private static final String RABBITMQ_NODENAME = "RABBITMQ_NODENAME";
    private static final Duration TEN_MINUTES_TIMEOUT = Duration.ofMinutes(10);

    private final GenericContainer<?> container;
    private final String nodeName;
    private final String rabbitHostName;
    private final String hostNameSuffix;
    private boolean paused;

    public static DockerRabbitMQ withCookieAndHostName(String hostNamePrefix, String clusterIdentity, String erlangCookie, Network network) {
        return new DockerRabbitMQ(Optional.ofNullable(hostNamePrefix), Optional.ofNullable(clusterIdentity), Optional.ofNullable(erlangCookie), Optional.of(network));
    }

    public static DockerRabbitMQ withoutCookie() {
        return new DockerRabbitMQ(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @SuppressWarnings("resource")
    private DockerRabbitMQ(Optional<String> hostNamePrefix, Optional<String> clusterIdentity, Optional<String> erlangCookie, Optional<Network> net) {
        paused = false;
        this.hostNameSuffix = clusterIdentity.orElse(UUID.randomUUID().toString());
        this.rabbitHostName = hostName(hostNamePrefix);
        this.container = new GenericContainer<>(Images.RABBITMQ)
            .withCreateContainerCmdModifier(cmd -> cmd.withName(this.rabbitHostName))
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName(this.rabbitHostName))
            .withExposedPorts(DEFAULT_RABBITMQ_PORT, DEFAULT_RABBITMQ_ADMIN_PORT)
            .waitingFor(waitStrategy())
            .withLogConsumer(frame -> LOGGER.debug(frame.getUtf8String()))
            .withTmpFs(ImmutableMap.of("/var/lib/rabbitmq/mnesia", "rw,noexec,nosuid,size=100m"));
        net.ifPresent(this.container::withNetwork);
        erlangCookie.ifPresent(cookie -> this.container.withEnv(RABBITMQ_ERLANG_COOKIE, cookie));
        this.nodeName = DEFAULT_RABBIT_NODE_NAME_PREFIX + "@" + this.rabbitHostName;
        this.container.withEnv(RABBITMQ_NODENAME, this.nodeName);
    }

    private WaitStrategy waitStrategy() {
        return new WaitAllStrategy()
            .withStrategy(Wait.forHttp("").forPort(DEFAULT_RABBITMQ_ADMIN_PORT)
                .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND)
                .withStartupTimeout(TEN_MINUTES_TIMEOUT))
            .withStrategy(new RabbitMQWaitStrategy(this, TEN_MINUTES_TIMEOUT))
            .withStartupTimeout(TEN_MINUTES_TIMEOUT);
    }

    private String hostName(Optional<String> hostNamePrefix) {
        return hostNamePrefix.orElse(DEFAULT_RABBIT_HOST_NAME_PREFIX) + "-" + hostNameSuffix;
    }

    private String getHostIp() {
        return container.getHost();
    }

    private Integer getPort() {
        return container.getMappedPort(DEFAULT_RABBITMQ_PORT);
    }

    private Integer getAdminPort() {
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
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
    }

    public void restart() throws Exception {
        stopApp();
        startApp();
        waitForReadyness();
    }

    public GenericContainer<?> container() {
        return container;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void join(DockerRabbitMQ rabbitMQ) throws Exception {
        stopApp();
        joinCluster(rabbitMQ);
        startApp();
        waitForReadyness();
    }

    public void stopApp() throws java.io.IOException, InterruptedException {
        String stdout = container()
            .execInContainer("rabbitmqctl", "stop_app")
            .getStdout();
        LOGGER.debug("stop_app: {}", stdout);
    }

    private void joinCluster(DockerRabbitMQ rabbitMQ) throws java.io.IOException, InterruptedException {
        String stdout = container()
            .execInContainer("rabbitmqctl", "join_cluster", rabbitMQ.getNodeName())
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

    public void forgetNode(String removalClusterNodeName) throws Exception {
        String stdout = container()
            .execInContainer("rabbitmqctl", "-n", this.nodeName, "forget_cluster_node", removalClusterNodeName)
            .getStdout();
        LOGGER.debug("forget_cluster_node: {}", stdout);

        startApp();
    }

    public void waitForReadyness() {
        waitStrategy().waitUntilReady(container);
    }

    public Address address() {
        return new Address(getHostIp(), getPort());
    }

    public URI amqpUri() throws URISyntaxException {
        return new URIBuilder()
                .setScheme("amqp")
                .setHost(getHostIp())
                .setPort(getPort())
                .build();
    }

    public URI managementUri() throws URISyntaxException {
        return new URIBuilder()
                .setScheme("http")
                .setHost(getHostIp())
                .setPort(getAdminPort())
                .build();
    }

    public void performIfRunning(ThrowingConsumer<DockerRabbitMQ> actionPerform) {
        if (container.isRunning()) {
            actionPerform.accept(this);
        }
    }

    public void pause() {
        if (!paused) {
            DockerClientFactory.instance().client().pauseContainerCmd(container.getContainerId()).exec();
            paused = true;
        }
    }

    public void unpause() {
        if (paused) {
            DockerClientFactory.instance().client().unpauseContainerCmd(container.getContainerId()).exec();
            paused = false;
        }
    }

    public RabbitMQConnectionFactory createRabbitConnectionFactory() throws URISyntaxException {
        return new RabbitMQConnectionFactory(getConfiguration());
    }

    public RabbitMQConfiguration getConfiguration() throws URISyntaxException {
        return RabbitMQConfiguration.builder()
            .amqpUri(amqpUri())
            .managementUri(managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(MAX_THREE_RETRIES)
            .minDelayInMs(MIN_DELAY_OF_TEN_MILLISECONDS)
            .connectionTimeoutInMs(CONNECTION_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .channelRpcTimeoutInMs(CHANNEL_RPC_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .handshakeTimeoutInMs(HANDSHAKE_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .shutdownTimeoutInMs(SHUTDOWN_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .networkRecoveryIntervalInMs(NETWORK_RECOVERY_INTERVAL_OF_ONE_HUNDRED_MILLISECOND)
            .build();
    }

    public RabbitMQConfiguration withQuorumQueueConfiguration() throws URISyntaxException {
        return RabbitMQConfiguration.builder()
            .amqpUri(amqpUri())
            .managementUri(managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(MAX_THREE_RETRIES)
            .minDelayInMs(MIN_DELAY_OF_TEN_MILLISECONDS)
            .connectionTimeoutInMs(CONNECTION_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .channelRpcTimeoutInMs(CHANNEL_RPC_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .handshakeTimeoutInMs(HANDSHAKE_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .shutdownTimeoutInMs(SHUTDOWN_TIMEOUT_OF_ONE_HUNDRED_MILLISECOND)
            .networkRecoveryIntervalInMs(NETWORK_RECOVERY_INTERVAL_OF_ONE_HUNDRED_MILLISECOND)
            .useQuorumQueues(true)
            .eventBusNotificationDurabilityEnabled(false)
            .queueTTL(Optional.of(3600000L))
            .build();
    }
}
