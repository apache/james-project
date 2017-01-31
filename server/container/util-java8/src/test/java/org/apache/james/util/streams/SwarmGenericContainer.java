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

package org.apache.james.util.streams;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.google.common.base.Strings;

public class SwarmGenericContainer extends GenericContainer<SwarmGenericContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwarmGenericContainer.class);
    private static final String DOCKER_CONTAINER = "DOCKER_CONTAINER";

    public SwarmGenericContainer(String dockerImageName) {
        super(dockerImageName);
    }

    public SwarmGenericContainer(ImageFromDockerfile imageFromDockerfile) {
        super(imageFromDockerfile);
    }

    public SwarmGenericContainer withAffinityToContainer() {
        String container = System.getenv(DOCKER_CONTAINER);
        if (Strings.isNullOrEmpty(container)) {
            LOGGER.warn("'DOCKER_CONTAINER' environment variable not found, dockering without affinity");
            return self();
        }
        List<String> envVariables = getEnv();
        envVariables.add("affinity:container==" + container);
        setEnv(envVariables);
        return self();
    }

    @SuppressWarnings("deprecation")
    public String getIp() {
        return getContainerInfo().getNetworkSettings().getIpAddress();
    }
}
