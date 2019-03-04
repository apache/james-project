/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.blob.objectstorage.aws;

import org.apache.james.util.Host;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class DockerAwsS3Container {

    private static final String AWS_S3_DOCKER_IMAGE = "scality/s3server:mem-6018536a";
    private static final int AWS_S3_PORT = 8000;
    private static final int ONE_TIME = 1;

    public static final String SCALITY_ACCESS_KEY_ID = "newAccessKey";
    public static final String SCALITY_SECRET_ACCESS_KEY = "newSecretKey";

    private final GenericContainer<?> awsS3Container;

    public DockerAwsS3Container() {
        this.awsS3Container = new GenericContainer<>(AWS_S3_DOCKER_IMAGE);
        this.awsS3Container
            .withExposedPorts(AWS_S3_PORT)
            .withEnv("S3BACKEND", "mem")
            .withEnv("SCALITY_ACCESS_KEY_ID", SCALITY_ACCESS_KEY_ID)
            .withEnv("SCALITY_SECRET_ACCESS_KEY", SCALITY_SECRET_ACCESS_KEY)
            .withEnv("LOG_LEVEL", "trace")
            .waitingFor(Wait.forLogMessage(".*\"message\":\"server started\".*\\n", ONE_TIME));
    }

    public void start() {
        awsS3Container.start();
    }

    public void stop() {
        awsS3Container.stop();
    }

    public Host getHost() {
        return Host.from(getIp(), getPort());
    }

    public String getIp() {
        return awsS3Container.getContainerIpAddress();
    }

    public int getPort() {
        return awsS3Container.getMappedPort(AWS_S3_PORT);
    }

    public String getEndpoint() {
        return "http://" + getIp() + ":" + getPort() + "/";
    }
}
