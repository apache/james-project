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

import static java.util.Collections.singletonMap;

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

    public static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse("rustfs/rustfs")
        .withTag("1.0.0-alpha.81");

    public static final int S3_PORT = 9000;
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
            .withExposedPorts(S3_PORT)
            .withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
            .withEnv("RUSTFS_VOLUMES", "/data/rustfs{0..3}")
            .withTmpFs(singletonMap("/data", "rw,mode=1777"))
            //.waitingFor(Wait.forLogMessage(".*started successfully.*", 1)
            .waitingFor(Wait.forLogMessage(".*Console WebUI.*", 2)
                .withStartupTimeout(Duration.ofMinutes(2)))
//            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-rustfs-s3-test-" + UUID.randomUUID()));
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-rustfs-s3-test"));
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
                .setScheme("http")
                .setHost(container.getHost())
                .setPort(container.getMappedPort(S3_PORT))
                .build()).get())
            .accessKeyId(S3_ACCESS_KEY)
            .secretKey(S3_SECRET_KEY)
            .trustAll(true)
            .build();
    }
}
