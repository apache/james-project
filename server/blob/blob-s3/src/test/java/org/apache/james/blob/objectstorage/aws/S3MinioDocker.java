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

    public static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse("chrislusf/seaweedfs")
        .withTag("4.07");

    public static final int S3_PORT = 8333;
    public static final int S3_HTTPS_PORT = 8334;
    public static final String S3_ACCESS_KEY = "testaccesskey";
    public static final String S3_SECRET_KEY = "testsecretkey";

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
            .withExposedPorts(S3_PORT, S3_HTTPS_PORT)
            .withEnv("AWS_ACCESS_KEY_ID", S3_ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", S3_SECRET_KEY)
            .withCommand("server", "-s3",
                "-s3.cert.file", "/opt/seaweedfs/certs/public.crt",
                "-s3.key.file", "/opt/seaweedfs/certs/private.key",
                "-s3.port.https", String.valueOf(S3_HTTPS_PORT),
                "-dir", "/data")
            .withClasspathResourceMapping("/minio/private.key",
                "/opt/seaweedfs/certs/private.key",
                BindMode.READ_ONLY)
            .withClasspathResourceMapping("/minio/public.crt",
                "/opt/seaweedfs/certs/public.crt",
                BindMode.READ_ONLY)
            .waitingFor(Wait.forLogMessage(".*Lock owner changed.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)))
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-seaweedfs-s3-test-" + UUID.randomUUID()));
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
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
                .setPort(container.getMappedPort(S3_HTTPS_PORT))
                .build()).get())
            .accessKeyId(S3_ACCESS_KEY)
            .secretKey(S3_SECRET_KEY)
            .trustAll(true)
            .build();
    }
}
