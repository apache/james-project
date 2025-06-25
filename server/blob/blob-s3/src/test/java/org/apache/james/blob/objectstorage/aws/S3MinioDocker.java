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

package org.apache.james.blob.objectstorage.aws;

import java.time.Duration;
import java.util.UUID;

import org.apache.http.client.utils.URIBuilder;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

public class S3MinioDocker {

    public static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse("minio/minio")
        .withTag("RELEASE.2025-06-13T11-33-47Z");

    public static final int MINIO_PORT = 9000;
    public static final int MINIO_WEB_ADMIN_PORT = 9090;
    public static final String MINIO_ROOT_USER = "minio";
    public static final String MINIO_ROOT_PASSWORD = "minio123";

    private final GenericContainer<?> container;

    public S3MinioDocker() {
        this.container = getContainer();
    }

    public S3MinioDocker(Network network) {
        this.container = getContainer()
            .withNetwork(network);
    }

    private GenericContainer<?> getContainer() {
        return new GenericContainer<>(DOCKER_IMAGE_NAME)
            .withExposedPorts(MINIO_PORT, MINIO_WEB_ADMIN_PORT)
            .withEnv("MINIO_ROOT_USER", MINIO_ROOT_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_ROOT_PASSWORD)
            .withCommand("server", "--certs-dir", "/opt/minio/certs", "/data", "--console-address", ":" + MINIO_WEB_ADMIN_PORT)
            .withClasspathResourceMapping("/minio/private.key",
                "/opt/minio/certs/private.key",
                BindMode.READ_ONLY)
            .withClasspathResourceMapping("/minio/public.crt",
                "/opt/minio/certs/public.crt",
                BindMode.READ_ONLY)
            .waitingFor(Wait.forLogMessage(".*MinIO Object Storage Server.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)))
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-minio-s3-test-" + UUID.randomUUID()));
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
            setupMC();
        }
    }

    public void stop() {
        container.stop();
    }

    public AwsS3AuthConfiguration getAwsS3AuthConfiguration() {
        Preconditions.checkArgument(container.isRunning(), "Container is not running");
        return AwsS3AuthConfiguration.builder()
            .endpoint(Throwing.supplier(() -> new URIBuilder()
                .setScheme("https")
                .setHost(container.getHost())
                .setPort(container.getMappedPort(MINIO_PORT))
                .build()).get())
            .accessKeyId(MINIO_ROOT_USER)
            .secretKey(MINIO_ROOT_PASSWORD)
            .trustAll(true)
            .build();
    }

    private void setupMC() {
        Preconditions.checkArgument(container.isRunning(), "Container is not running");
        Throwing.runnable(() -> container.execInContainer("mc", "alias", "set", "--insecure", "james", "https://localhost:9000", MINIO_ROOT_USER, MINIO_ROOT_PASSWORD)).run();
    }

    public void flushAll() {
        // Remove all objects
        Throwing.runnable(() -> container.execInContainer("mc", "--insecure", "rm", "--recursive", "--force", "--dangerous", "james/")).run();
        // Remove all buckets
        Throwing.runnable(() -> container.execInContainer("mc", "--insecure", "rb", "--force", "--dangerous", "james/")).run();
    }
}
