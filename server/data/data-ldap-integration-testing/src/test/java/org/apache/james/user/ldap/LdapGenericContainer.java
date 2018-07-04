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
package org.apache.james.user.ldap;

import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class LdapGenericContainer extends ExternalResource {

    public static final int DEFAULT_LDAP_PORT = 389;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String domain;
        private String password;

        private Builder() {
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public LdapGenericContainer build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(domain), "'domain' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(password), "'password' is mandatory");
            return new LdapGenericContainer(createContainer());
        }

        private SwarmGenericContainer createContainer() {
            return new SwarmGenericContainer(
                new ImageFromDockerfile()
                    .withFileFromClasspath("populate.ldif", "ldif-files/populate.ldif")
                    .withFileFromClasspath("Dockerfile", "ldif-files/Dockerfile"))
                .withAffinityToContainer()
                .withEnv("SLAPD_DOMAIN", domain)
                .withEnv("SLAPD_PASSWORD", password)
                .withEnv("SLAPD_CONFIG_PASSWORD", password)
                .withExposedPorts(LdapGenericContainer.DEFAULT_LDAP_PORT)
                .waitingFor(new HostPortWaitStrategy());
        }
    }

    private final SwarmGenericContainer container;

    private LdapGenericContainer(SwarmGenericContainer container) {
        this.container = container;
    }

    @Override
    protected void before() {
        start();
    }

    @Override
    protected void after() {
        stop();
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        container.pause();
    }

    public void unpause() {
        container.unpause();
    }

    public String getLdapHost() {
        return "ldap://" +
                container.getContainerIp() +
                ":" +
                LdapGenericContainer.DEFAULT_LDAP_PORT;
    }
}
