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
package org.apache.james.mpt.imapmailbox.cyrus.host;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;

public class Docker {
    
    private static final int IMAP_PORT = 143;
    private static final String EXPOSED_IMAP_PORT = IMAP_PORT + "/tcp";
    private static final HostConfig ALL_PORTS_HOST_CONFIG = HostConfig.builder()
        .publishAllPorts(true)
        .build();

    
    private final DefaultDockerClient dockerClient;
    private final ContainerConfig containerConfig;

    public Docker(String imageName)  {
        containerConfig = ContainerConfig.builder()
                .image(imageName)
                .networkDisabled(false)
                .exposedPorts(ImmutableSet.of(EXPOSED_IMAP_PORT))
                .build();
        
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
            dockerClient.pull(imageName);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public ContainerCreation start() throws Exception {
        ContainerCreation container = dockerClient.createContainer(containerConfig);
        dockerClient.startContainer(container.id(), ALL_PORTS_HOST_CONFIG);
        return container;
    }

    public void stop(ContainerCreation container) {
        try {
            dockerClient.killContainer(container.id());
            dockerClient.removeContainer(container.id(), true);
        } catch (DockerException e) {
            Throwables.propagate(e);
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
    }
    
    public String getHost(ContainerCreation container) {
        return dockerClient.getHost();
    }

    public int getIMAPPort(ContainerCreation container) {
        try {
            return Integer.valueOf(
                    Iterables.getOnlyElement(
                            dockerClient.inspectContainer(
                                    container.id())
                                    .networkSettings()
                                    .ports()
                                    .get(EXPOSED_IMAP_PORT))
                            .hostPort());
        } catch (NumberFormatException e) {
            throw Throwables.propagate(e);
        } catch (DockerException e) {
            throw Throwables.propagate(e);
        } catch (InterruptedException e) {
            throw Throwables.propagate(e);        }
    }
    
    public void createUser(ContainerCreation container, String user, String password) throws DockerException, InterruptedException {
        String createUserCommand = String.format("echo %s | saslpasswd2 -u test -c %s -p", password, user);
        String execId = dockerClient.execCreate(container.id(), new String[] {"/bin/bash", "-c", createUserCommand});
        dockerClient.execStart(execId);
    }
}
