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

package org.apache.james.modules;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitializationOperations;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

class InitializationOperationsTest {
    @Test
    void initModulesShouldNotFailWhenBindingsInWrongOrder() throws Exception {
        Injector injector = Guice.createInjector(new StartablesModule(),
                new UnorderedBindingsModule());

        injector.getInstance(InitializationOperations.class).initModules();

        assertThat(injector.getInstance(A.class).isConfigured()).isTrue();
        assertThat(injector.getInstance(B.class).isConfigured()).isTrue();
    }

    private static class UnorderedBindingsModule extends StartablesModule {

        @Override
        protected void configure() {
            bind(B.class).in(Scopes.SINGLETON);
            bind(A.class).in(Scopes.SINGLETON);
            bind(C.class).in(Scopes.SINGLETON);
    
            Multibinder.newSetBinder(binder(), InitializationOperation.class).addBinding().to(BInitializationOperation.class);
            Multibinder.newSetBinder(binder(), InitializationOperation.class).addBinding().to(AInitializationOperation.class);
        }
    }

    private static class AInitializationOperation implements InitializationOperation {
        private final A a;

        @Inject
        private AInitializationOperation(A a) {
            this.a = a;
        }

        @Override
        public void initModule() {
            try {
                a.configure(null);
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Class<? extends Startable> forClass() {
            return A.class;
        }
    }

    private static class BInitializationOperation implements InitializationOperation {
        private final B b;

        @Inject
        private BInitializationOperation(B b) {
            this.b = b;
        }

        @Override
        public void initModule() {
            try {
                b.configure(null);
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Class<? extends Startable> forClass() {
            return B.class;
        }
    }

    private static class A implements Configurable {
        @SuppressWarnings("unused")
        private final C c;
        private boolean configured;

        @Inject
        private A(C c) {
            this.c = c;
            this.configured = false;
        }

        @Override
        public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
            configured = true;
        }

        public boolean isConfigured() {
            return configured;
        }
    }

    private static class B implements Configurable {
        private final A a;
        @SuppressWarnings("unused")
        private final C c;
        private boolean configured;

        @Inject
        private B(A a, C c) {
            this.a = a;
            this.c = c;
            this.configured = false;
        }

        @Override
        public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
            configured = a.isConfigured();
        }

        public boolean isConfigured() {
            return configured;
        }
    }

    private static class C {
    }
}
