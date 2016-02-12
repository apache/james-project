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
package org.apache.james.mpt.smtp.utils;


import java.util.Map;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;

public class DockerRule extends ExternalResource {

    public static final String DOCKER_SERVICE_URL = "unix:///var/run/docker.sock";
    private final DockerClient dockerClient;
    private final HostConfig hostConfig;
    private ContainerCreation container;
    private Map<String, String> portBindings;

    public DockerRule(String imageName, String... cmd) throws DockerException, InterruptedException {
        this(imageName, DOCKER_SERVICE_URL, cmd);
    }

    public DockerRule(String imageName, String dockerServiceUrl, String... cmd) throws DockerException, InterruptedException {
        portBindings = Maps.newHashMap();

        hostConfig = HostConfig.builder()
                .publishAllPorts(true)
                .build();

        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(imageName)
                .networkDisabled(false)
                .cmd(cmd)
                .hostConfig(hostConfig)
                .build();

        dockerClient = new DefaultDockerClient(dockerServiceUrl);

        dockerClient.pull(imageName);
        container = dockerClient.createContainer(containerConfig);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return super.apply(base, description);
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        dockerClient.startContainer(container.id());
        portBindings.putAll(extractPortBindings());
    }

    public String getContainerIp() throws DockerException, InterruptedException {
        return dockerClient.inspectContainer(container.id())
                .networkSettings()
                .ipAddress();
    }
    
    private Map<String, String> extractPortBindings() throws DockerException,
            InterruptedException {
        return Maps.transformValues(
                dockerClient.inspectContainer(container.id())
                .networkSettings()
                .ports(),
                x -> Iterables.getOnlyElement(x).hostPort());
    }

    @Override
    protected void after() {
        super.after();
        try {
            dockerClient.killContainer(container.id());
            dockerClient.removeContainer(container.id(), true);
        } catch (DockerException | InterruptedException e) {
            Throwables.propagate(e);
        }
    }

    public Map<String, String> getPortBindings() {
        return portBindings;
    }

} 