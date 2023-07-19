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

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.client.utils.URIBuilder;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class LdapGenericContainer extends ExternalResource {

    public static final int DEFAULT_LDAP_PORT = 389;
    public static final int DEFAULT_LDAPS_PORT = 636;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<String> dockerFilePrefix = Optional.empty();
        private String domain;
        private String password;

        private Builder() {
        }

        public Builder dockerFilePrefix(String prefix) {
            this.dockerFilePrefix = Optional.of(prefix);
            return this;
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

        private DockerContainer createContainer() {
            return DockerContainer.fromName(Images.OPEN_LDAP)
                .withClasspathResourceMapping(dockerFilePrefix.orElse("") + "ldif-files/populate.ldif",
                    "/container/service/slapd/assets/config/bootstrap/ldif/data.ldif", BindMode.READ_ONLY)
                .withAffinityToContainer()
                .withEnv("LDAP_DOMAIN", domain)
                .withEnv("LDAP_ADMIN_PASSWORD", password)
                .withEnv("LDAP_CONFIG_PASSWORD", password)
                .withEnv("LDAP_TLS_VERIFY_CLIENT", "try")
                .withExposedPorts(DEFAULT_LDAP_PORT, DEFAULT_LDAPS_PORT)
                .withCommands("--copy-service", "--loglevel", "debug")
                .withName("james-testing-openldap-" + UUID.randomUUID())
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*slapd starting\\n").withTimes(1)
                    .withStartupTimeout(Duration.ofMinutes(3)))
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
        }
    }

    private final DockerContainer container;

    private LdapGenericContainer(DockerContainer container) {
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
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        container.pause();
    }

    public void unpause() {
        if (isPaused()) {
            container.unpause();
        }
    }

    private boolean isPaused() {
        return container.getContainer().getDockerClient().inspectContainerCmd(container.getContainer().getContainerId())
            .exec()
            .getState()
            .getPaused();
    }

    public String getLdapHost() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("ldap")
            .setHost(container.getContainer().getHost())
            .setPort(container.getMappedPort(DEFAULT_LDAP_PORT))
            .build()).get().toString();
    }

    public String getLdapsHost() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("ldaps")
            .setHost(container.getContainer().getHost())
            .setPort(container.getMappedPort(DEFAULT_LDAPS_PORT))
            .build()).get().toString();
    }

    /**
     * @return LDAPS URL on LDAP port (as string)
     */
    public String getLdapsBadHost() {
        return "ldaps://" +
                container.getContainerIp() +
                ":" +
                LdapGenericContainer.DEFAULT_LDAP_PORT;
    }
}
