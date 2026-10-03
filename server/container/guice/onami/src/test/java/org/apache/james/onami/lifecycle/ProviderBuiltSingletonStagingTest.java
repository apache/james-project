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

package org.apache.james.onami.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

class ProviderBuiltSingletonStagingTest {
    interface Stoppable {
    }

    static class StopRecorder implements Stoppable {
        private final String name;
        private final List<String> stops;

        StopRecorder(String name, List<String> stops) {
            this.name = name;
            this.stops = stops;
        }

        @PreDestroy
        void stop() {
            stops.add(name);
        }
    }

    static class InheritingStopRecorder extends StopRecorder {
        InheritingStopRecorder(String name, List<String> stops) {
            super(name, stops);
        }
    }

    static class InjectedStopRecorder extends StopRecorder {
        @Inject
        InjectedStopRecorder(List<String> stops) {
            super("injected", stops);
        }
    }

    static class OverridingStopRecorder extends StopRecorder {
        OverridingStopRecorder(String name, List<String> stops) {
            super(name, stops);
        }

        @Override
        @PreDestroy
        void stop() {
            super.stop();
        }
    }

    static class InjectedOverridingStopRecorder extends StopRecorder {
        @Inject
        InjectedOverridingStopRecorder(List<String> stops) {
            super("injected-overriding", stops);
        }

        @Override
        @PreDestroy
        void stop() {
            super.stop();
        }
    }

    static class PrivateStopBase {
        final List<String> stops;

        PrivateStopBase(List<String> stops) {
            this.stops = stops;
        }

        @PreDestroy
        private void stop() {
            stops.add("base");
        }
    }

    static class PrivateStopDerived extends PrivateStopBase {
        PrivateStopDerived(List<String> stops) {
            super(stops);
        }

        @PreDestroy
        private void stop() {
            stops.add("derived");
        }
    }

    static class StopRecorderProvider implements Provider<Stoppable> {
        private final List<String> stops;

        @Inject
        StopRecorderProvider(List<String> stops) {
            this.stops = stops;
        }

        @Override
        public Stoppable get() {
            return new StopRecorder("provider-class", stops);
        }
    }

    private final List<String> stops = new ArrayList<>();
    private final PreDestroyModule preDestroyModule = new PreDestroyModule();

    private Injector injector(Module module) {
        return Guice.createInjector(preDestroyModule, binder -> binder.bind(new TypeLiteral<List<String>>() { }).toInstance(stops), module);
    }

    @Test
    void preDestroyOfProviderBuiltSingletonShouldBeCalledOnce() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder stopRecorder() {
                return new StopRecorder("provided", stops);
            }
        });
        injector.getInstance(StopRecorder.class);
        injector.getInstance(StopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("provided");
    }

    @Test
    void inheritedPreDestroyOfProviderBuiltSingletonShouldBeCalled() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder stopRecorder() {
                return new InheritingStopRecorder("inheriting", stops);
            }
        });
        injector.getInstance(StopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("inheriting");
    }

    @Test
    void preDestroyOfConstructorInjectedSingletonShouldBeCalledOnce() {
        Injector injector = injector(binder -> binder.bind(InjectedStopRecorder.class).in(Scopes.SINGLETON));
        injector.getInstance(InjectedStopRecorder.class);
        injector.getInstance(InjectedStopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("injected");
    }

    @Test
    void preDestroyOfInjectedSingletonReturnedByProviderShouldBeCalledOnce() {
        Injector injector = injector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(InjectedStopRecorder.class).in(Scopes.SINGLETON);
            }

            @Provides
            @Singleton
            Stoppable stoppable(InjectedStopRecorder stopRecorder) {
                return stopRecorder;
            }
        });
        injector.getInstance(Stoppable.class);
        injector.getInstance(InjectedStopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("injected");
    }

    @Test
    void preDestroyOfProviderBuiltSingletonExposedUnderTwoKeysShouldBeCalledOnce() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder stopRecorder() {
                return new StopRecorder("provided", stops);
            }

            @Provides
            @Singleton
            Stoppable stoppable(StopRecorder stopRecorder) {
                return stopRecorder;
            }
        });
        injector.getInstance(Stoppable.class);
        injector.getInstance(StopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("provided");
    }

    @Test
    void providerBuiltSingletonShouldBeStoppedBeforeItsDependencies() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder dependency() {
                return new StopRecorder("dependency", stops);
            }

            @Provides
            @Singleton
            Stoppable dependent(StopRecorder dependency) {
                return new StopRecorder("dependent", stops);
            }
        });
        injector.getInstance(Stoppable.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("dependent", "dependency");
    }

    @Test
    void preDestroyOfUnscopedProviderBuiltObjectShouldNotBeCalled() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            StopRecorder stopRecorder() {
                return new StopRecorder("unscoped", stops);
            }
        });
        injector.getInstance(StopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).isEmpty();
    }

    @Test
    void preDestroyOfProviderBuiltSingletonInjectedLaterShouldBeCalledOnce() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder stopRecorder() {
                return new StopRecorder("provided", stops);
            }
        });
        injector.injectMembers(injector.getInstance(StopRecorder.class));

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("provided");
    }

    @Test
    void overriddenPreDestroyOfProviderBuiltSingletonShouldBeCalledOnce() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            StopRecorder stopRecorder() {
                return new OverridingStopRecorder("overriding", stops);
            }
        });
        injector.getInstance(StopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("overriding");
    }

    @Test
    void overriddenPreDestroyOfConstructorInjectedSingletonShouldBeCalledOnce() {
        Injector injector = injector(binder -> binder.bind(InjectedOverridingStopRecorder.class).in(Scopes.SINGLETON));
        injector.getInstance(InjectedOverridingStopRecorder.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("injected-overriding");
    }

    @Test
    void privatePreDestroyMethodsOfSubclassAndSuperclassShouldBothBeCalled() {
        Injector injector = injector(new AbstractModule() {
            @Provides
            @Singleton
            PrivateStopBase privateStop() {
                return new PrivateStopDerived(stops);
            }
        });
        injector.getInstance(PrivateStopBase.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactlyInAnyOrder("derived", "base");
    }

    @Test
    void preDestroyOfSingletonBuiltByProviderClassShouldBeCalledOnce() {
        Injector injector = injector(binder -> binder.bind(Stoppable.class).toProvider(StopRecorderProvider.class).in(Scopes.SINGLETON));
        injector.getInstance(Stoppable.class);
        injector.getInstance(Stoppable.class);

        preDestroyModule.getStager().stage();

        assertThat(stops).containsExactly("provider-class");
    }
}
