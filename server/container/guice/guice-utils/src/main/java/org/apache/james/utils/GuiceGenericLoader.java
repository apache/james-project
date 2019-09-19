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

import java.util.Optional;
import java.util.stream.Stream;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

public class GuiceGenericLoader {
    private static final Optional<Module> NO_CHILD_MODULE = Optional.empty();

    public static class InvocationPerformer<T> {
        private final Injector injector;
        private final ExtendedClassLoader extendedClassLoader;
        private final NamingScheme namingSheme;
        private final Optional<Module> childModule;

        private InvocationPerformer(Injector injector, ExtendedClassLoader extendedClassLoader, NamingScheme namingSheme, Optional<Module> childModule) {
            this.injector = injector;
            this.extendedClassLoader = extendedClassLoader;
            this.namingSheme = namingSheme;
            this.childModule = childModule;
        }

        public T instanciate(ClassName className) throws ClassNotFoundException {
            Class<T> clazz = locateClass(className, namingSheme);

            Injector resolvedInjector = childModule.map(this.injector::createChildInjector)
                .orElse(this.injector);

            return resolvedInjector.getInstance(clazz);
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

    @Inject
    public GuiceGenericLoader(Injector injector, ExtendedClassLoader extendedClassLoader) {
        this.injector = injector;
        this.extendedClassLoader = extendedClassLoader;
    }

    public <T> T instanciate(ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY, NO_CHILD_MODULE)
            .instanciate(className);
    }

    public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
        return new InvocationPerformer<T>(injector, extendedClassLoader, namingSheme, NO_CHILD_MODULE);
    }

    public <T> InvocationPerformer<T> withChildModule(Module childModule) {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY, Optional.of(childModule));
    }
}
