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
package org.apache.james.webadmin.integration;

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.DockerCassandraRule;
import org.apache.james.DockerElasticSearchRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestDockerESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.Runnables;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.Throwing;

public class CassandraJmapExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    public interface JamesLifeCyclePolicy {
        JamesLifeCyclePolicy FOR_EACH_TEST = serverSupplier -> JamesLifecycleHandler.builder()
            .beforeAll(Optional::empty)
            .beforeEach(() -> Optional.of(serverSupplier.get()))
            .afterEach(GuiceJamesServer::stop)
            .afterAll(guiceJamesServer -> { });
        JamesLifeCyclePolicy COMMON_TO_ALL_TESTS = serverSupplier -> JamesLifecycleHandler.builder()
            .beforeAll(() -> Optional.of(serverSupplier.get()))
            .beforeEach(Optional::empty)
            .afterEach(guiceJamesServer -> { })
            .afterAll(GuiceJamesServer::stop);

        JamesLifecycleHandler createHandler(Supplier<GuiceJamesServer> serverSupplier);
    }

    public static class JamesLifecycleHandler {
        public interface Builder {
            @FunctionalInterface
            interface RequiresBeforeAll {
                RequiresBeforeEach beforeAll(Supplier<Optional<GuiceJamesServer>> beforeAll);

            }

            @FunctionalInterface
            interface RequiresBeforeEach {
                RequiresAfterEach beforeEach(Supplier<Optional<GuiceJamesServer>> beforeEach);
            }

            @FunctionalInterface
            interface RequiresAfterEach {
                RequiresAfterAll afterEach(Consumer<GuiceJamesServer> afterAll);
            }

            @FunctionalInterface
            interface RequiresAfterAll {
                JamesLifecycleHandler afterAll(Consumer<GuiceJamesServer> afterAll);
            }
        }

        public static Builder.RequiresBeforeAll builder() {
            return beforeAll -> beforeEach -> afterEach -> afterAll -> new JamesLifecycleHandler(beforeAll, beforeEach, afterEach, afterAll);
        }

        private final Supplier<Optional<GuiceJamesServer>> beforeAll;
        private final Supplier<Optional<GuiceJamesServer>> beforeEach;
        private final Consumer<GuiceJamesServer> afterEach;
        private final Consumer<GuiceJamesServer>  afterAll;

        JamesLifecycleHandler(Supplier<Optional<GuiceJamesServer>> beforeAll, Supplier<Optional<GuiceJamesServer>> beforeEach, Consumer<GuiceJamesServer> afterEach, Consumer<GuiceJamesServer> afterAll) {
            this.beforeAll = beforeAll;
            this.beforeEach = beforeEach;
            this.afterEach = afterEach;
            this.afterAll = afterAll;
        }

        Optional<GuiceJamesServer> beforeAll() {
            return beforeAll.get()
                .map(FunctionalUtils.toFunction(Throwing.consumer(GuiceJamesServer::start)));
        }

        Optional<GuiceJamesServer> beforeEach() {
            return beforeEach.get()
                .map(FunctionalUtils.toFunction(Throwing.consumer(GuiceJamesServer::start)));
        }

        void afterEach(GuiceJamesServer guiceJamesServer) {
            afterEach.accept(guiceJamesServer);
        }

        void afterAll(GuiceJamesServer guiceJamesServer) {
            afterAll.accept(guiceJamesServer);
        }
    }

    private static final int LIMIT_TO_20_MESSAGES = 20;

    private final TemporaryFolder temporaryFolder;
    private final DockerCassandraRule cassandra;
    private final DockerElasticSearchRule elasticSearchRule;
    private final JamesLifecycleHandler jamesLifecycleHandler;
    private GuiceJamesServer james;

    public CassandraJmapExtension() {
        this(JamesLifeCyclePolicy.FOR_EACH_TEST);
    }

    public CassandraJmapExtension(JamesLifeCyclePolicy jamesLifeCyclePolicy) {
        this.temporaryFolder = new TemporaryFolder();
        this.cassandra = new DockerCassandraRule();
        this.elasticSearchRule = new DockerElasticSearchRule();
        this.jamesLifecycleHandler = jamesLifeCyclePolicy.createHandler(jamesSupplier());
    }

    private GuiceJamesServer james() throws IOException {
        Configuration configuration = Configuration.builder()
                .workingDirectory(temporaryFolder.newFolder())
                .configurationFromClasspath()
                .build();

        return GuiceJamesServer.forConfiguration(configuration)
                .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE).overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_20_MESSAGES))
                .overrideWith(new TestDockerESMetricReporterModule(elasticSearchRule.getDockerEs().getHttpHost()))
                .overrideWith(cassandra.getModule())
                .overrideWith(elasticSearchRule.getModule())
                .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION))
                .overrideWith(new UnauthorizedModule())
                .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()));
    }

    private Supplier<GuiceJamesServer> jamesSupplier() {
        return Throwing.supplier(this::james);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        temporaryFolder.create();
        Runnables.runParallel(cassandra::start, elasticSearchRule::start);
        james = jamesLifecycleHandler.beforeAll().orElse(james);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        jamesLifecycleHandler.afterAll(james);
        Runnables.runParallel(cassandra::stop, elasticSearchRule.getDockerEs()::cleanUpData);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        james = jamesLifecycleHandler.beforeEach().orElse(james);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        jamesLifecycleHandler.afterEach(james);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == GuiceJamesServer.class;
    }

    public GuiceJamesServer getJames() {
        return james;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return james;
    }
}
