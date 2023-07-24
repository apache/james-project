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

import java.io.Closeable;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.apache.james.util.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DockerCassandra {

    /**
     * James uses a non privileged Cassandra user(role) in testing. To be able to do that, the non privileged user needs to be
     * prepared along with a created keyspace.
     *
     * This process is done by using the default user provided by docker cassandra, it has the capability of creating roles,
     * keyspaces, and granting permissions to those entities.
     */
    public static class CassandraResourcesManager implements Closeable {
        private static final String CASSANDRA_SUPER_USER = "cassandra";
        private static final String CASSANDRA_SUPER_USER_PASSWORD = "cassandra";

        private final CqlSession privilegedCluster;

        private CassandraResourcesManager(DockerCassandra cassandra) {
            privilegedCluster = ClusterFactory.createWithoutKeyspace(cassandra.superUserConfigurationBuilder().build());
        }

        @Override
        public void close() {
            privilegedCluster.closeAsync();
        }

        public Mono<Void> initializeKeyspace(KeyspaceConfiguration configuration) {
            return KeyspaceFactory.createKeyspace(configuration, privilegedCluster)
                .then(grantPermissionToTestingUser(configuration.getKeyspace()));
        }

        public Mono<Void> provisionNonPrivilegedUser() {
            return Mono.from(privilegedCluster.executeReactive("CREATE ROLE IF NOT EXISTS " + CASSANDRA_TESTING_USER + " WITH PASSWORD = '" + CASSANDRA_TESTING_PASSWORD + "' AND LOGIN = true"))
                .then();
        }

        private Mono<Void> grantPermissionToTestingUser(String keyspace) {
            return Mono.from(privilegedCluster.executeReactive("GRANT ALL PERMISSIONS ON KEYSPACE " + keyspace + " TO " + CASSANDRA_TESTING_USER))
                .then();
        }
    }

    public static ClusterConfiguration.Builder configurationBuilder(Host... hosts) {
        return ClusterConfiguration.builder()
            .hosts(hosts)
            .username(CASSANDRA_TESTING_USER)
            .password(CASSANDRA_TESTING_PASSWORD)
            .maxRetry(RELAXED_RETRIES);
    }

    private static final Logger logger = LoggerFactory.getLogger(DockerCassandra.class);
    public static final String KEYSPACE = "testing";
    public static final String CACHE_KEYSPACE = "testing_cache";
    private static final int RELAXED_RETRIES = 2;

    public static final String CASSANDRA_TESTING_USER = "james_testing";
    public static final String CASSANDRA_TESTING_PASSWORD = "james_testing_password";

    @FunctionalInterface
    public interface AdditionalDockerFileStep {
        AdditionalDockerFileStep IDENTITY = builder -> builder;

        DockerfileBuilder applyStep(DockerfileBuilder builder);
    }

    /**
     * @return a string to append to image names in order to avoid conflict with concurrent builds
     */
    private static String buildSpecificImageDiscriminator() {
        // If available try to access the image shared by all maven projects
        // This avoids rebuilding one for each maven surefire fork.
        // BUILD_ID should be set by the execution context, here JenkinsFile
        return Optional.ofNullable(System.getenv("BUILD_ID"))
            // Default to an image discriminator specific to this JVM
            .orElse(UUID.randomUUID().toString());
    }

    private static final int CASSANDRA_PORT = 9042;
    private static final int CASSANDRA_MEMORY = 750;

    private static final String CASSANDRA_CONFIG_DIR = "$CASSANDRA_CONFIG";
    private static final String JVM_OPTIONS = CASSANDRA_CONFIG_DIR + "/jvm.options";

    private final GenericContainer<?> cassandraContainer;
    private final DockerClient client;

    @SuppressWarnings("resource")
    public DockerCassandra() {
        this("cassandra_4_1_3-" + buildSpecificImageDiscriminator(), AdditionalDockerFileStep.IDENTITY);
    }

    private DockerCassandra(String imageName, AdditionalDockerFileStep additionalSteps) {
        client = DockerClientFactory.instance().client();
        EventsCmd eventsCmd = client.eventsCmd().withEventTypeFilter(EventType.IMAGE).withImageFilter(imageName);
        eventsCmd.exec(new ResultCallback<Event>() {
            @Override
            public void onStart(Closeable closeable) {

            }

            @Override
            public void onNext(Event object) {
                logger.info(object.toString());
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("event stream failure", throwable);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void close() {
            }
        });
        boolean doNotDeleteImageAfterUsage = false;
        cassandraContainer = new GenericContainer<>(
            new ImageFromDockerfile(imageName,doNotDeleteImageAfterUsage)
                .withDockerfileFromBuilder(builder ->
                    additionalSteps.applyStep(builder
                        .from("cassandra:4.1.3")
                        .env("CASSANDRA_CONFIG", "/etc/cassandra")
                        .run("echo \"-Xms" + CASSANDRA_MEMORY + "M\" >> " + JVM_OPTIONS
                            + "&& echo \"-Xmx" + CASSANDRA_MEMORY + "M\" >> " + JVM_OPTIONS
                            + "&& echo \"-Dcassandra.skip_wait_for_gossip_to_settle=0\" >> " + JVM_OPTIONS
                            + "&& echo \"-Dcassandra.load_ring_state=false\" >> " + JVM_OPTIONS
                            + "&& echo \"-Dcassandra.initial_token=1 \" >> " + JVM_OPTIONS
                            + "&& echo \"-Dcassandra.num_tokens=nil \" >> " + JVM_OPTIONS
                            + "&& echo \"-Dcassandra.allocate_tokens_for_local_replication_factor=nil \" >> " + JVM_OPTIONS
                            + "&& sed -i 's/auto_snapshot: true/auto_snapshot: false/g' /etc/cassandra/cassandra.yaml"
                            + "&& echo 'authenticator: PasswordAuthenticator' >> /etc/cassandra/cassandra.yaml"
                            + "&& echo 'authorizer: org.apache.cassandra.auth.CassandraAuthorizer' >> /etc/cassandra/cassandra.yaml"))
                        .build()))
            .withTmpFs(ImmutableMap.of("/var/lib/cassandra", "rw,noexec,nosuid,size=200m"))
            .withExposedPorts(CASSANDRA_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-cassandra-test-" + UUID.randomUUID()))
            .withLogConsumer(DockerCassandra::displayDockerLog);
        cassandraContainer
            .waitingFor(new CassandraWaitStrategy(cassandraContainer));
    }

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String().trim());
    }

    public void start() {
        if (!cassandraContainer.isRunning()) {
            cassandraContainer.start();
            try (CassandraResourcesManager resourcesManager = administrator()) {
                resourcesManager.provisionNonPrivilegedUser()
                    .then(Flux.merge(
                        resourcesManager.initializeKeyspace(mainKeyspaceConfiguration()),
                        resourcesManager.initializeKeyspace(cacheKeyspaceConfiguration()))
                        .then())
                    .block();
            }
        }
    }

    public CassandraResourcesManager administrator() {
        return new CassandraResourcesManager(this);
    }

    public void stop() {
        if (cassandraContainer.isRunning()) {
            cassandraContainer.stop();
        }
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
        client.pauseContainerCmd(cassandraContainer.getContainerId()).exec();
    }

    public void unpause() {
        if (isPaused()) {
            client.unpauseContainerCmd(cassandraContainer.getContainerId()).exec();
        }
    }

    private boolean isPaused() {
        return client.inspectContainerCmd(cassandraContainer.getContainerId())
            .exec()
            .getState()
            .getPaused();
    }

    public ClusterConfiguration.Builder configurationBuilder() {
        return configurationBuilder(getHost());
    }

    public ClusterConfiguration.Builder superUserConfigurationBuilder() {
        return ClusterConfiguration.builder()
            .host(getHost())
            .username(CassandraResourcesManager.CASSANDRA_SUPER_USER)
            .password(CassandraResourcesManager.CASSANDRA_SUPER_USER_PASSWORD)
            .createKeyspace()
            .maxRetry(RELAXED_RETRIES);
    }

    public static KeyspaceConfiguration mainKeyspaceConfiguration() {
        return KeyspaceConfiguration.builder()
            .keyspace(KEYSPACE)
            .replicationFactor(1)
            .disableDurableWrites();
    }

    public static KeyspaceConfiguration cacheKeyspaceConfiguration() {
        return KeyspaceConfiguration.builder()
            .keyspace(CACHE_KEYSPACE)
            .replicationFactor(1)
            .disableDurableWrites();
    }
}
