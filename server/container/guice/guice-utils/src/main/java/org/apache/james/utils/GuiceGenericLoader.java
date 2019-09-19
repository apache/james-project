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

import java.util.stream.Stream;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class GuiceGenericLoader {
    private static final Module NO_CHILD_MODULE = binder -> { };

    @VisibleForTesting
    public static GuiceGenericLoader forTesting(ExtendedClassLoader extendedClassLoader) {
        return new GuiceGenericLoader(Guice.createInjector(), extendedClassLoader, ExtensionConfiguration.DEFAULT);
    }

    public static class InvocationPerformer<T> {
        private final Injector injector;
        private final ExtendedClassLoader extendedClassLoader;
        private final NamingScheme namingSheme;
        private final Module childModule;

        private InvocationPerformer(Injector injector, ExtendedClassLoader extendedClassLoader, NamingScheme namingSheme, Module childModule) {
            this.injector = injector;
            this.extendedClassLoader = extendedClassLoader;
            this.namingSheme = namingSheme;
            this.childModule = childModule;
        }

        public T instanciate(ClassName className) throws ClassNotFoundException {
            Class<T> clazz = locateClass(className, namingSheme);
            return injector.createChildInjector(childModule)
                .getInstance(clazz);
        }

        private Class<T> locateClass(ClassName className, NamingScheme namingScheme) throws ClassNotFoundException {
            return namingScheme.toFullyQualifiedClassNames(className)
                .flatMap(this::tryLocateClass)
                .findFirst()
                .orElseThrow(() -> new ClassNotFoundException(className.getName()));
        }

        private Stream<Class<T>> tryLocateClass(FullyQualifiedClassName className) {
            try {
                return Stream.of(extendedClassLoader.locateClass(className));
            } catch (ClassNotFoundException e) {
                return Stream.empty();
            }
        }
    }

    private final Injector injector;
    private final ExtendedClassLoader extendedClassLoader;
    private final Module additionalExtensionBindings;

    @Inject
    public GuiceGenericLoader(Injector injector, ExtendedClassLoader extendedClassLoader, ExtensionConfiguration extensionConfiguration) {
        this.injector = injector;
        this.extendedClassLoader = extendedClassLoader;

        this.additionalExtensionBindings = Modules.combine(extensionConfiguration.getAdditionalGuiceModulesForExtensions()
            .stream()
            .map(Throwing.function(this::<Module>instanciateNoChildModule))
            .collect(Guavate.toImmutableList()));
    }

    private  <T> T instanciateNoChildModule(ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY, NO_CHILD_MODULE)
            .instanciate(className);
    }

    public <T> T instanciate(ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY, additionalExtensionBindings)
            .instanciate(className);
    }

    public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
        return new InvocationPerformer<T>(injector, extendedClassLoader, namingSheme, additionalExtensionBindings);
    }

    public <T> InvocationPerformer<T> withChildModule(Module childModule) {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY, Modules.combine(additionalExtensionBindings, childModule));
    }
}
