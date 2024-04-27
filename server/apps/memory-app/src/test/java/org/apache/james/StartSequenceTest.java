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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

class StartSequenceTest {
    static class A implements Startable {
        public boolean started = false;

        public void start() {
            started = true;
        }
    }

    static class D implements Startable {
        public boolean started = false;

        public void start() {
            started = true;
        }
    }

    static class F implements Startable {
        public boolean started = false;

        public void start() {
            started = true;
        }
    }

    static class B implements Startable {
        private final A a;

        @Inject
        B(A a) {
            this.a = a;
        }

        public void start() {
            assert a.started;
        }
    }

    static class C implements Startable {
        private final D d;

        @Inject
        C(D d) {
            this.d = d;
        }

        public void start() {
            assert d.started;
        }
    }

    static class E implements Startable {
        private final F f;
        private final A a;

        @Inject
        E(F f, A a) {
            this.f = f;
            this.a = a;
        }

        public void start() {
            assert f.started;
            assert a.started;
        }
    }

    static class ConfigurableTestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(B.class).in(Scopes.SINGLETON);
            bind(C.class).in(Scopes.SINGLETON);
            bind(D.class).in(Scopes.SINGLETON);
            bind(F.class).in(Scopes.SINGLETON);
        }

        @Provides
        @Singleton
        E e(F f, A a) {
            return new E(f, a);
        }

        @Provides
        @Singleton
        A a() {
            return new A();
        }

        @ProvidesIntoSet
        InitializationOperation initA(A a) {
            return InitilizationOperationBuilder
                .forClass(A.class)
                .init(a::start);
        }

        @ProvidesIntoSet
        InitializationOperation initB(B b) {
            return InitilizationOperationBuilder
                .forClass(B.class)
                .init(b::start);
        }

        @ProvidesIntoSet
        InitializationOperation initC(C c) {
            return InitilizationOperationBuilder
                .forClass(C.class)
                .init(c::start);
        }

        @ProvidesIntoSet
        InitializationOperation initD(D d) {
            return InitilizationOperationBuilder
                .forClass(D.class)
                .init(d::start);
        }

        @ProvidesIntoSet
        InitializationOperation initD(E e) {
            return InitilizationOperationBuilder
                .forClass(E.class)
                .init(e::start)
                .requires(ImmutableList.of(A.class, F.class));
        }

        @ProvidesIntoSet
        InitializationOperation initD(F f) {
            return InitilizationOperationBuilder
                .forClass(F.class)
                .init(f::start);
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new ConfigurableTestModule()))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .disableAutoStart()
        .build();

    @Test
    void providesShouldBeTakenIntoAccountByTheStartSequence(GuiceJamesServer server) {
        assertThatCode(server::start).doesNotThrowAnyException();
    }
}
