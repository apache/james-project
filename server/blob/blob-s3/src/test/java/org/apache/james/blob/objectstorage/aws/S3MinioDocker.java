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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class S3MinioDocker {

    public static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse("ghcr.io/bikeshedder/garage-single-node")
        .withTag("v2.1.0-bs-dev");

    public static final int S3_PORT = 3900;
    public static final String S3_ACCESS_KEY = "GK0f3c2715a60440468081cf3f";
    public static final String S3_SECRET_KEY = "96045f1bb0f9c1c2077ff61bdd8be86314374c50e72b2e823be0d577fd2ce9a6";

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
            .withEnv("GARAGE_ACCESS_KEY_ID", S3_ACCESS_KEY)
            .withEnv("GARAGE_SECRET_ACCESS_KEY", S3_SECRET_KEY)
            .withEnv("GARAGE_BUCKETS", "my-test-bucket:public")
            .withTmpFs(ImmutableMap.of("/var/lib/garage/meta", "rw,mode=1777",
                "/var/lib/garage/data", "rw,mode=1777"))
            .waitingFor(Wait.forLogMessage(".*Bootstrapping complete.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)))
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-garage-s3-test-" + UUID.randomUUID()));
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
            setupKey();
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
            .build();
    }

    private void setupKey() {
        Preconditions.checkArgument(container.isRunning(), "Container is not running");
        Throwing.runnable(() -> container.execInContainer("/garage", "key", "allow", "--create-bucket", S3_ACCESS_KEY)).run();
        Throwing.runnable(() -> container.execInContainer("/garage", "bucket", "delete", "my-test-bucket", "--yes")).run();
    }
}
