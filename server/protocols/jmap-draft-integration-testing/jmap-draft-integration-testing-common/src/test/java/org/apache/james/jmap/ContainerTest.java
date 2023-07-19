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
package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class ContainerTest {

    @Rule
    public DockerContainer container = DockerContainer.fromName("nginx:1.25")
        .withAffinityToContainer()
        .withExposedPorts(80)
        .waitingFor(new HttpWaitStrategy()
            .forStatusCode(200)
            .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));

    @Test
    public void containerShouldBeReachableOnExposedPort() throws IOException, URISyntaxException {
        Response response = Request.Get(new URIBuilder()
            .setScheme("http")
            .setHost(container.getHostIp())
            .setPort(container.getMappedPort(80)).build())
            .execute();

        assertThat(response.returnResponse().getStatusLine().getStatusCode())
            .isEqualTo(200);
    }
}
