/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership. The ASF licenses this file    *
 * to you under the Apache License, Version 2.0 (the             *
 * "License"); you may not use this file except in compliance   *
 * with the License. You may obtain a copy of the License at     *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the     *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.blob.objectstorage.aws;

import java.net.URI;
import java.time.Duration;

import org.apache.james.blob.api.BucketName;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class DockerCephS3Container {
    private static final DockerImageName CEPH_IMAGE = DockerImageName.parse("quay.io/ceph/demo")
        .withTag("main-30dc8b9a-squid-centos-stream9-x86_64");
    private static final int RGW_PORT = 8080;

    public static final String ACCESS_KEY = "james-access-key";
    public static final String SECRET_KEY = "james-secret-key";
    public static final Region REGION = Region.of("us-east-1");
    public static final BucketName TEST_BUCKET = BucketName.of("james-ceph-test");

    private final GenericContainer<?> container;

    public DockerCephS3Container() {
        container = new GenericContainer<>(CEPH_IMAGE)
            .withExposedPorts(RGW_PORT)
            .withEnv("CEPH_DEMO_UID", "james")
            .withEnv("CEPH_DEMO_ACCESS_KEY", ACCESS_KEY)
            .withEnv("CEPH_DEMO_SECRET_KEY", SECRET_KEY)
            .withEnv("CEPH_DEMO_BUCKET", TEST_BUCKET.asString())
            .withEnv("CEPH_PUBLIC_NETWORK", "0.0.0.0/0")
            .withEnv("MON_IP", "127.0.0.1")
            .withEnv("NETWORK_AUTO_DETECT", "4")
            .withEnv("RGW_FRONTEND_PORT", Integer.toString(RGW_PORT))
            .withEnv("RGW_NAME", "localhost")
            .withCommand("demo")
            .waitingFor(Wait.forLogMessage(".*\\/opt\\/ceph-container\\/bin\\/demo: SUCCESS.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(5)))
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withPlatform("linux/amd64"));
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public URI getEndpoint() {
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(RGW_PORT));
    }

    public AwsS3AuthConfiguration getAwsS3AuthConfiguration() {
        return AwsS3AuthConfiguration.builder()
            .endpoint(getEndpoint())
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .build();
    }
}
