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

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.apache.james.util.Host;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class DockerAwsS3Container {
    public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final String AWS_S3_DOCKER_IMAGE = "registry.scality.com/cloudserver/cloudserver:8.7.25";
    private static final int AWS_S3_PORT = 8000;
    private static final int ONE_TIME = 1;

    public static final Region REGION = Region.of(software.amazon.awssdk.regions.Region.EU_WEST_1.id());
    public static final String ACCESS_KEY_ID = "newAccessKey";
    public static final String SECRET_ACCESS_KEY = "newSecretKey";

    private final GenericContainer<?> awsS3Container;

    public DockerAwsS3Container() {
        this.awsS3Container = new GenericContainer<>(AWS_S3_DOCKER_IMAGE)
            .withExposedPorts(AWS_S3_PORT)
            .withEnv("S3BACKEND", "mem")
            .withEnv("SCALITY_ACCESS_KEY_ID", ACCESS_KEY_ID)
            .withEnv("SCALITY_SECRET_ACCESS_KEY", SECRET_ACCESS_KEY)
            .withEnv("LOG_LEVEL", "trace")
            .withEnv("REMOTE_MANAGEMENT_DISABLE", "1")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-s3-test-" + UUID.randomUUID()))
            .waitingFor(Wait.forLogMessage(".*\"message\":\"server started\".*\\n", ONE_TIME))
            .withStartupTimeout(STARTUP_TIMEOUT);
    }

    public void start() {
        awsS3Container.start();
    }

    public void stop() {
        awsS3Container.stop();
    }

    public void pause() {
        awsS3Container.getDockerClient().pauseContainerCmd(awsS3Container.getContainerId()).exec();
    }

    public void unpause() {
        awsS3Container.getDockerClient().unpauseContainerCmd(awsS3Container.getContainerId()).exec();
    }

    public boolean isPaused() {
        return awsS3Container.getDockerClient().inspectContainerCmd(awsS3Container.getContainerId())
            .exec()
            .getState()
            .getPaused();
    }

    public Host getHost() {
        return Host.from(getIp(), getPort());
    }

    public String getIp() {
        return awsS3Container.getHost();
    }

    public int getPort() {
        start();
        return awsS3Container.getMappedPort(AWS_S3_PORT);
    }

    public URI getEndpoint() {
        return URI.create("http://" + getIp() + ":" + getPort() + "/");
    }

    public GenericContainer<?> getRawContainer() {
        return awsS3Container;
    }

    public void tryDeleteAllData() throws S3Exception {
        try (var client = S3AsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(DockerAwsS3Container.ACCESS_KEY_ID, DockerAwsS3Container.SECRET_ACCESS_KEY)))
            .endpointOverride(getEndpoint())
            .region(DockerAwsS3Container.REGION.asAws())
            .build()) {
            client.listBuckets().join().buckets()
                .stream()
                .map(Bucket::name)
                .forEach(bucketName -> client.deleteBucket(builder -> builder.bucket(bucketName)).join());
        }
    }
}
