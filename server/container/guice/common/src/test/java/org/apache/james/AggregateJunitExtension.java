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

import java.util.List;
import java.util.Optional;

import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;

public class AggregateJunitExtension implements RegistrableExtension {

    private final List<? extends RegistrableExtension> registrableExtensions;

    public AggregateJunitExtension(List<? extends RegistrableExtension> registrableExtensions) {
        this.registrableExtensions = registrableExtensions;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        Runnables.runParallel(Flux.fromIterable(registrableExtensions)
                    .map(ext -> Throwing.runnable(() -> ext.beforeAll(extensionContext))));
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        Runnables.runParallel(Flux.fromIterable(registrableExtensions)
                    .map(ext -> Throwing.runnable(() -> ext.beforeEach(extensionContext))));
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        Runnables.runParallel(Flux.fromIterable(Lists.reverse(registrableExtensions))
                    .map(ext -> Throwing.runnable(() -> ext.afterEach(extensionContext))));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Runnables.runParallel(Flux.fromIterable(Lists.reverse(registrableExtensions))
                    .map(ext -> Throwing.runnable(() -> ext.afterAll(extensionContext))));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionSupportParam(parameterContext, extensionContext)
            .isPresent();
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionSupportParam(parameterContext, extensionContext)
            .map(extension -> extension.resolveParameter(parameterContext, extensionContext))
            .orElseThrow(() -> new IllegalArgumentException("parameter is not resolved by registrableExtensions"));
    }

    private Optional<? extends RegistrableExtension> extensionSupportParam(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return registrableExtensions.stream()
            .filter(extension -> extension.supportsParameter(parameterContext, extensionContext))
            .findFirst();
    }
}
