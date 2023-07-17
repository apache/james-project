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

package org.apache.james.spamassassin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.Base58;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SpamAssassinExtension implements BeforeAllCallback, AfterEachCallback, ParameterResolver {
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(30);
    private static final String UNIQUE_IDENTIFIER = Base58.randomString(16).toLowerCase();

    private static final boolean DELETE_ON_EXIT = false;
    private static final GenericContainer<?> spamAssassinContainer  = new GenericContainer<>(
        new ImageFromDockerfile("james-spamassassin/" + UNIQUE_IDENTIFIER, DELETE_ON_EXIT)
            .withFileFromClasspath("Dockerfile", "docker/spamassassin/Dockerfile")
            .withFileFromClasspath("local.cf", "docker/spamassassin/local.cf")
            .withFileFromClasspath("run.sh", "docker/spamassassin/run.sh")
            .withFileFromClasspath("spamd.sh", "docker/spamassassin/spamd.sh")
            .withFileFromClasspath("rule-update.sh", "docker/spamassassin/rule-update.sh")
            .withFileFromClasspath("bayes_pg.sql", "docker/spamassassin/bayes_pg.sql"))
        .withCreateContainerCmdModifier(cmd -> cmd.withName("spam-assassin-" + UUID.randomUUID().toString()))
        .withStartupTimeout(STARTUP_TIMEOUT)
        .withExposedPorts(783)
        .withTmpFs(ImmutableMap.of("/var/lib/postgresql/data", "rw,noexec,nosuid,size=200m"))
        .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));

    static {
        spamAssassinContainer.start();
    }

    private SpamAssassin spamAssassin;

    @Override
    public void beforeAll(ExtensionContext context) {
        spamAssassin = new SpamAssassin(spamAssassinContainer);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        clearSpamAssassinDatabase();
    }

    private void clearSpamAssassinDatabase() {
        try {
            spamAssassin.clearSpamAssassinDatabase();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == SpamAssassin.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return spamAssassin;
    }

    public SpamAssassin getSpamAssassin() {
        return spamAssassin;
    }

    public static class SpamAssassin {
        private static final int SPAMASSASSIN_PORT = 783;

        private final String ip;
        private final int bindingPort;
        private final GenericContainer<?> spamAssassinContainer;

        private SpamAssassin(GenericContainer<?> spamAssassinContainer) {
            this.spamAssassinContainer = spamAssassinContainer;
            this.ip = spamAssassinContainer.getHost();
            this.bindingPort = spamAssassinContainer.getMappedPort(SPAMASSASSIN_PORT);
        }

        public String getIp() {
            return ip;
        }
    
        public int getBindingPort() {
            return bindingPort;
        }

        public void train(String user) throws IOException, URISyntaxException {
            Path spamPath = Paths.get(ClassLoader.getSystemResource("spamassassin_db/spam").toURI());
            Path hamPath = Paths.get(ClassLoader.getSystemResource("spamassassin_db/ham").toURI());

            Flux.merge(
                Mono.fromRunnable(Throwing.runnable(() -> train(user, spamPath, TrainingKind.SPAM))),
                Mono.fromRunnable(Throwing.runnable(() -> train(user, hamPath, TrainingKind.HAM))))
                .subscribeOn(Schedulers.boundedElastic())
                .blockLast();
        }

        private void train(String user, Path folder, TrainingKind trainingKind) throws IOException {
            spamAssassinContainer.getDockerClient().copyArchiveToContainerCmd(spamAssassinContainer.getContainerId())
                .withHostResource(folder.toAbsolutePath().toString())
                .withRemotePath("/root")
                .exec();
            try (Stream<Path> paths = Files.walk(folder)) {
                paths.parallel()
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(Throwing.consumer(file -> spamAssassinContainer.execInContainer("sa-learn",
                        trainingKind.saLearnExtensionName(), "-u", user,
                        "/root/" + trainingKind.name().toLowerCase(Locale.US) + "/" +  file.getName())));
            }
        }

        private enum TrainingKind {
            SPAM("--spam"), HAM("--ham");

            private String saLearnExtensionName;

            TrainingKind(String saLearnExtensionName) {
                this.saLearnExtensionName = saLearnExtensionName;
            }

            public String saLearnExtensionName() {
                return saLearnExtensionName;
            }
        }

        public void sync(String user) throws UnsupportedOperationException, IOException, InterruptedException {
            spamAssassinContainer.execInContainer("sa-learn", "--sync", "-u", user);
        }

        public void dump(String user) throws UnsupportedOperationException, IOException, InterruptedException {
            spamAssassinContainer.execInContainer("sa-learn", "--dump", "magic", "-u", user);
        }

        public void clear(String user) throws UnsupportedOperationException, IOException, InterruptedException {
            spamAssassinContainer.execInContainer("sa-learn", "--clear", "-u", user);
        }

        public void clearSpamAssassinDatabase() throws UnsupportedOperationException, IOException, InterruptedException {
            spamAssassinContainer.execInContainer("sa-learn", "--clear");
        }
    }

}
