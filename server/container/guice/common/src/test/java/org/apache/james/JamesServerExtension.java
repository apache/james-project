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

package org.apache.james;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingBiConsumer;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class JamesServerExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesServerExtension.class);

    interface ThrowingFunction<P, T> {
        T apply(P parameter) throws Exception;
    }

    interface AwaitCondition {
        void await();
    }

    public enum Lifecycle {
        // Restarts the server for each class, including nested classes
        PER_CLASS(JamesServerExtension::start, (extension, context) -> { }, (extension, context) -> { }, JamesServerExtension::stop),
        // Restarts the server for the enclosing class, it will ignore nested classes
        PER_ENCLOSING_CLASS(
            (extension, context) -> {
                if (!isNested(context)) {
                    extension.start(context);
                }
            },
            (extension, context) -> { },
            (extension, context) -> { },
            (extension, context) -> {
                if (!isNested(context)) {
                    extension.stop(context);
                }
            }),
        // Restarts the server for each test (default)
        PER_TEST((extension, context) -> { }, JamesServerExtension::start, JamesServerExtension::stop, (extension, context) -> { });

        private static boolean isNested(ExtensionContext context) {
            return context.getTestClass()
                .map(clazz -> clazz.isAnnotationPresent(Nested.class))
                .orElse(false);
        }

        private final ThrowingBiConsumer<JamesServerExtension, ExtensionContext> beforeAll;
        private final ThrowingBiConsumer<JamesServerExtension, ExtensionContext> beforeEach;
        private final ThrowingBiConsumer<JamesServerExtension, ExtensionContext> afterEach;
        private final ThrowingBiConsumer<JamesServerExtension, ExtensionContext> afterAll;

        Lifecycle(ThrowingBiConsumer<JamesServerExtension, ExtensionContext> beforeAll,
                  ThrowingBiConsumer<JamesServerExtension, ExtensionContext> beforeEach,
                  ThrowingBiConsumer<JamesServerExtension, ExtensionContext> afterEach,
                  ThrowingBiConsumer<JamesServerExtension, ExtensionContext> afterAll) {
            this.beforeAll = beforeAll;
            this.beforeEach = beforeEach;
            this.afterEach = afterEach;
            this.afterAll = afterAll;
        }
    }

    private final TemporaryFolderRegistrableExtension folderRegistrableExtension;
    private final ThrowingFunction<File, GuiceJamesServer> serverSupplier;
    private final RegistrableExtension registrableExtension;
    private final boolean autoStart;
    private final Lifecycle lifecycle;
    private final AwaitCondition awaitCondition;

    private GuiceJamesServer guiceJamesServer;

    JamesServerExtension(RegistrableExtension registrableExtension, ThrowingFunction<File, GuiceJamesServer> serverSupplier,
                         AwaitCondition awaitCondition, boolean autoStart, Lifecycle lifecycle) {
        this.registrableExtension = registrableExtension;
        this.serverSupplier = serverSupplier;
        this.lifecycle = lifecycle;
        this.folderRegistrableExtension = new TemporaryFolderRegistrableExtension();
        this.autoStart = autoStart;
        this.awaitCondition = awaitCondition;
    }

    public GuiceJamesServer getGuiceJamesServer() {
        return guiceJamesServer;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        registrableExtension.beforeAll(extensionContext);
        lifecycle.beforeAll.accept(this, extensionContext);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        lifecycle.beforeEach.accept(this, extensionContext);
    }

    private void start(ExtensionContext extensionContext) throws Exception {
        folderRegistrableExtension.beforeEach(extensionContext);
        registrableExtension.beforeEach(extensionContext);
        guiceJamesServer = serverSupplier.apply(createTmpDir());
        if (autoStart) {
            Mono.fromRunnable(Throwing.runnable(() -> {
                    try {
                        guiceJamesServer.start();
                    } catch (Exception e) {
                        LOGGER.error("Error {} while starting James Extension. May retry to restart James...", e.getMessage());
                        guiceJamesServer.stop();
                        throw e;
                    }
                }))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                    .scheduler(Schedulers.boundedElastic()))
                .block();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        lifecycle.afterEach.accept(this, extensionContext);
    }

    private void stop(ExtensionContext extensionContext) throws Exception {
        guiceJamesServer.stop();
        registrableExtension.afterEach(extensionContext);
        folderRegistrableExtension.afterEach(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        lifecycle.afterAll.accept(this, extensionContext);
        registrableExtension.afterAll(extensionContext);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == GuiceJamesServer.class)
            || registrableExtension.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (registrableExtension.supportsParameter(parameterContext, extensionContext)) {
            return registrableExtension.resolveParameter(parameterContext, extensionContext);
        }
        return guiceJamesServer;
    }

    private File createTmpDir() {
        try {
            return folderRegistrableExtension.getTemporaryFolder().newFolder();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void await() {
        awaitCondition.await();
    }
}
