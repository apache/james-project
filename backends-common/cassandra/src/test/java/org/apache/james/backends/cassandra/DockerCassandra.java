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
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import com.google.common.collect.ImmutableMap;

public class DockerCassandra {

    /**
     * James uses a non privileged Cassandra user(role) in testing. To be able to do that, the non privileged user needs to be
     * prepared along with a created keyspace.
     *
     * This process is done by using the default user provided by docker cassandra, it has the capability of creating roles,
     * keyspaces, and granting permissions to those entities.
     */
    public static class CassandraResourcesManager {

        private static final String CASSANDRA_SUPER_USER = "cassandra";
        private static final String CASSANDRA_SUPER_USER_PASSWORD = "cassandra";

        private final DockerCassandra cassandra;

        private CassandraResourcesManager(DockerCassandra cassandra) {
            this.cassandra = cassandra;
        }

        public void initializeKeyspace(KeyspaceConfiguration configuration) {
            try (Cluster privilegedCluster = ClusterFactory.create(cassandra.superUserConfigurationBuilder().build(),
                    CassandraConsistenciesConfiguration.DEFAULT)) {
                provisionNonPrivilegedUser(privilegedCluster);
                KeyspaceFactory.createKeyspace(configuration, privilegedCluster);
                grantPermissionToTestingUser(privilegedCluster, configuration.getKeyspace());
            }
        }

        private void provisionNonPrivilegedUser(Cluster privilegedCluster) {
            try (Session session = privilegedCluster.newSession()) {
                session.execute("CREATE ROLE IF NOT EXISTS " + CASSANDRA_TESTING_USER + " WITH PASSWORD = '" + CASSANDRA_TESTING_PASSWORD + "' AND LOGIN = true");
            }
        }

        private void grantPermissionToTestingUser(Cluster privilegedCluster, String keyspace) {
            try (Session session = privilegedCluster.newSession()) {
                session.execute("GRANT CREATE ON KEYSPACE " + keyspace + " TO " + CASSANDRA_TESTING_USER);
                session.execute("GRANT SELECT ON KEYSPACE " + keyspace + " TO " + CASSANDRA_TESTING_USER);
                session.execute("GRANT MODIFY ON KEYSPACE " + keyspace + " TO " + CASSANDRA_TESTING_USER);
                // some tests require dropping in setups
                session.execute("GRANT DROP ON KEYSPACE " + keyspace + " TO " + CASSANDRA_TESTING_USER);
            }
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
    private static final int CASSANDRA_MEMORY = 650;

    private static final String CASSANDRA_CONFIG_DIR = "$CASSANDRA_CONFIG";
    private static final String JVM_OPTIONS = CASSANDRA_CONFIG_DIR + "/jvm.options";

    private final GenericContainer<?> cassandraContainer;
    private final DockerClient client;

    @SuppressWarnings("resource")
    public DockerCassandra() {
        this("cassandra_3_11_3-" + buildSpecificImageDiscriminator(), AdditionalDockerFileStep.IDENTITY);
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
                logger.error("event stream failure",throwable);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void close() throws IOException {
            }
        });
        boolean doNotDeleteImageAfterUsage = false;
        cassandraContainer = new GenericContainer<>(
            new ImageFromDockerfile(imageName,doNotDeleteImageAfterUsage)
                .withDockerfileFromBuilder(builder ->
                    additionalSteps.applyStep(builder
                        .from("cassandra:3.11.3")
                        .env("ENV CASSANDRA_CONFIG", "/etc/cassandra")
                        .run("echo \"-Xms" + CASSANDRA_MEMORY + "M\" >> " + JVM_OPTIONS)
                        .run("echo \"-Xmx" + CASSANDRA_MEMORY + "M\" >> " + JVM_OPTIONS)
                        .run("sed", "-i", "s/auto_snapshot: true/auto_snapshot: false/g", "/etc/cassandra/cassandra.yaml")
                        .run("echo 'authenticator: PasswordAuthenticator' >> /etc/cassandra/cassandra.yaml")
                        .run("echo 'authorizer: org.apache.cassandra.auth.CassandraAuthorizer' >> /etc/cassandra/cassandra.yaml"))
                        .build()))
            .withTmpFs(ImmutableMap.of("/var/lib/cassandra", "rw,noexec,nosuid,size=200m"))
            .withExposedPorts(CASSANDRA_PORT)
            .withLogConsumer(DockerCassandra::displayDockerLog);
        cassandraContainer
            .waitingFor(new CassandraWaitStrategy(cassandraContainer));
    }

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String());
    }

    public void start() {
        if (!cassandraContainer.isRunning()) {
            cassandraContainer.start();
            administrator().initializeKeyspace(mainKeyspaceConfiguration());
            administrator().initializeKeyspace(cacheKeyspaceConfiguration());
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
