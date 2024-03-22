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

package org.apache.james.utils;

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

public class UserStartables implements Startable {
    public static class Module extends AbstractModule {
        @ProvidesIntoSet
        @Singleton
        InitializationOperation initializationOperations(UserStartables startables) {
            return InitilizationOperationBuilder
                .forClass(UserStartables.class)
                .init(startables::start);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UserStartables.class);

    private final GuiceGenericLoader loader;
    private final ExtensionConfiguration extensionConfiguration;

    @Inject
    public UserStartables(GuiceGenericLoader loader, ExtensionConfiguration extensionConfiguration) {
        this.loader = loader;
        this.extensionConfiguration = extensionConfiguration;
    }

    public void start() {
        extensionConfiguration.getStartables()
            .stream()
            .map(Throwing.<ClassName, UserDefinedStartable>function(loader::instantiate))
            .peek(module -> LOGGER.info("Starting {}", module.getClass().getCanonicalName()))
            .forEach(UserDefinedStartable::start);
    }
}
