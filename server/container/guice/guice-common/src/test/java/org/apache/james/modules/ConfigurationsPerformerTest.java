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

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationsPerformer;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class ConfigurationsPerformerTest {

    @Test
    public void initModulesShouldNotFailWhenBindingsInWrongOrder() throws Exception {
        Injector injector = Guice.createInjector(new ConfigurablesModule(),
                new UnorderedBindingsModule());

        injector.getInstance(ConfigurationsPerformer.class).initModules();

        assertThat(injector.getInstance(A.class).isConfigured()).isTrue();
        assertThat(injector.getInstance(B.class).isConfigured()).isTrue();
    }

    private static class UnorderedBindingsModule extends ConfigurablesModule {

        @Override
        protected void configure() {
            bind(B.class).in(Scopes.SINGLETON);
            bind(A.class).in(Scopes.SINGLETON);
            bind(C.class).in(Scopes.SINGLETON);
    
            Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(BConfigurationPerformer.class);
            Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(AConfigurationPerformer.class);
        }
    }

    private static class AConfigurationPerformer implements ConfigurationPerformer {

        private final A a;

        @Inject
        private AConfigurationPerformer(A a) {
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
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(A.class);
        }
    }

    private static class BConfigurationPerformer implements ConfigurationPerformer {

        private final B b;

        @Inject
        private BConfigurationPerformer(B b) {
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
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(B.class);
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
        public void configure(HierarchicalConfiguration config) throws ConfigurationException {
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
        public void configure(HierarchicalConfiguration config) throws ConfigurationException {
            configured = a.isConfigured();
        }

        public boolean isConfigured() {
            return configured;
        }
    }

    private static class C {
    }

    @Test
    public void initModulesShouldBePerformedOneTimeWhenConfigurableModuleContainsMultipleDependencies() throws Exception {
        Injector injector = Guice.createInjector(new ConfigurablesModule(),
                new DualResponsibilityConfigurationPerformerModule());

        injector.getInstance(ConfigurationsPerformer.class).initModules();

        assertThat(injector.getInstance(A.class).isConfigured()).isTrue();
        assertThat(injector.getInstance(B.class).isConfigured()).isTrue();
    }

    private static class DualResponsibilityConfigurationPerformer implements ConfigurationPerformer {

        private final A a;
        private final B b;
        private boolean configured;

        @Inject
        private DualResponsibilityConfigurationPerformer(A a, B b) {
            this.a = a;
            this.b = b;
            this.configured = false;
        }

        @Override
        public void initModule() {
            if (configured) {
                throw new RuntimeException("Already configured");
            }

            try {
                a.configure(null);
                b.configure(null);
                configured = true;
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(A.class, B.class);
        }
    }

    private static class DualResponsibilityConfigurationPerformerModule extends ConfigurablesModule {

        @Override
        protected void configure() {
            bind(B.class).in(Scopes.SINGLETON);
            bind(A.class).in(Scopes.SINGLETON);
            bind(C.class).in(Scopes.SINGLETON);
    
            Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(DualResponsibilityConfigurationPerformer.class);
        }
    }
}
