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

import static com.google.inject.matcher.Matchers.any;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

class MultiLifeCycleTestCase {
    @Test
    void testOrdering() {
        Module lifeCycleModule = new TestLifeCycleModule(asList(TestAnnotationA.class, TestAnnotationB.class, TestAnnotationC.class));
        MultiLifeCycleObject obj = Guice.createInjector(lifeCycleModule).getInstance(MultiLifeCycleObject.class);
        assertThat(obj.toString()).isEqualTo("aaabbbc");
    }

    public static class Foo {
        @Inject
        public Foo(Stager<TestAnnotationA> stager) {
            System.out.println(stager.getStage());
        }
    }

    @Test
    void testStaging() {
        Module moduleA = new TestLifeCycleStageModule(new DefaultStager<>(TestAnnotationA.class));
        Module moduleB = new TestLifeCycleStageModule(new DefaultStager<>(TestAnnotationB.class));
        Module moduleC = new TestLifeCycleStageModule(new DefaultStager<>(TestAnnotationC.class));

        Injector injector = Guice.createInjector(moduleA, moduleB, moduleC);
        MultiLifeCycleObject obj = injector.getInstance(MultiLifeCycleObject.class);

        assertThat("").isEqualTo(obj.toString());

        injector.getInstance(LifeCycleStageModule.key(TestAnnotationA.class)).stage();
        assertThat(obj.toString()).isEqualTo("aaa");
        injector.getInstance(LifeCycleStageModule.key(TestAnnotationB.class)).stage();
        assertThat(obj.toString()).isEqualTo("aaabbb");
        injector.getInstance(LifeCycleStageModule.key(TestAnnotationC.class)).stage();
        assertThat(obj.toString()).isEqualTo("aaabbbc");

        injector.getInstance(Foo.class);
    }

    @Test
    void testStagingOrdering() {
        Module moduleA = new TestLifeCycleStageModule(new DefaultStager<>(TestAnnotationA.class, DefaultStager.Order.FIRST_IN_FIRST_OUT));
        Module moduleB = new TestLifeCycleStageModule(new DefaultStager<>(TestAnnotationB.class, DefaultStager.Order.FIRST_IN_LAST_OUT));

        final StringBuilder str = new StringBuilder();
        Module m = new AbstractModule() {
            @Override
            protected void configure() {
                binder().bind(StringBuilder.class).toInstance(str);
            }
        };

        Injector injector = Guice.createInjector(moduleA, moduleB, m);
        injector.getInstance(StageObject1.class);
        injector.getInstance(StageObject2.class);

        injector.getInstance(LifeCycleStageModule.key(TestAnnotationA.class)).stage();
        assertThat(str.toString()).isEqualTo("1a2a");
        str.setLength(0);

        injector.getInstance(LifeCycleStageModule.key(TestAnnotationB.class)).stage();
        assertThat(str.toString()).isEqualTo("2b1b");
    }

    private static class TestLifeCycleModule extends LifeCycleModule {

        private final List<? extends Class<? extends Annotation>> annotations;

        public TestLifeCycleModule(List<? extends Class<? extends Annotation>> annotations) {
            this.annotations = annotations;
        }

        @Override
        protected void configure() {
            bindLifeCycle(annotations, any());
        }
    }

    private static class TestLifeCycleStageModule extends LifeCycleStageModule {

        private final Stager<?> stager;

        public TestLifeCycleStageModule(Stager<?> stager) {
            this.stager = stager;
        }

        @Override
        protected void configureBindings() {
            bindStager(stager);
        }
    }
}
