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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.util.Types;

/**
 * Guice module to register methods to be invoked when {@link Stager#stage()} is invoked.
 * <p/>
 * Module instance have has so it must not be used to construct more than one {@link com.google.inject.Injector}.
 */
public abstract class LifeCycleStageModule extends LifeCycleModule {

    private List<BindingBuilder<?>> bindings;

    /**
     * Convenience to generate the correct key for retrieving stagers from an injector.
     * E.g.
     * <p/>
     * <code><pre>
     * Stager&lt;MyAnnotation&gt; stager = injector.getInstance( LifeCycleStageModule.key( MyAnnotation.class ) );
     * </pre></code>
     *
     * @param stage the annotation that represents this stage and the methods with this annotation
     * @param <A>   the Annotation type
     * @return the Guice key to use for accessing the stager for the input stage
     */
    public static <A extends Annotation> Key<Stager<A>> key(Class<A> stage) {
        return Key.get(type(stage));
    }

    private static <A extends Annotation> TypeLiteral<Stager<A>> type(Class<A> stage) {
        ParameterizedType parameterizedType = Types.newParameterizedTypeWithOwner(null, Stager.class, stage);
        //noinspection unchecked
        @SuppressWarnings("unchecked") // TODO
            TypeLiteral<Stager<A>> stagerType = (TypeLiteral<Stager<A>>) TypeLiteral.get(parameterizedType);
        return stagerType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void configure() {
        if (bindings != null) {
            throw new IllegalStateException("Re-entry is not allowed");
        }
        bindings = new ArrayList<>();
        try {
            configureBindings();
            for (BindingBuilder<?> binding : bindings) {
                bind(binding);
            }
        } finally {
            bindings = null;
        }
    }

    private <A extends Annotation> void bind(BindingBuilder<A> binding) {
        final Stager<A> stager = binding.stager;
        final StageableTypeMapper typeMapper = binding.typeMapper;
        bind(type(stager.getStage())).toInstance(stager);

        bindListener(binding.typeMatcher, new AbstractMethodTypeListener(asList(stager.getStage())) {
            @Override
            protected <I> void hear(final Method stageMethod, final TypeLiteral<I> parentType,
                                    final TypeEncounter<I> encounter, final Class<? extends Annotation> annotationType) {
                encounter.register((InjectionListener<I>) injectee -> {
                    Stageable stageable = new StageableMethod(stageMethod, injectee);
                    stager.register(stageable);
                    typeMapper.registerType(stageable, parentType);
                });
            }
        });
    }

    protected abstract void configureBindings();

    protected final <A extends Annotation> MapperBinding bindStager(Stager<A> stager) {
        BindingBuilder<A> builder = new BindingBuilder<>(checkNotNull(stager, "Argument 'stager' must be not null"));
        bindings.add(builder);
        return builder;
    }

    protected interface MatcherBinding {
        /**
         * Sets the filter for injectee types.
         *
         * @param typeMatcher the filter for injectee types.
         */
        void matching(Matcher<? super TypeLiteral<?>> typeMatcher);
    }

    protected interface MapperBinding extends MatcherBinding {
        /**
         * Sets the container to register mappings from {@link Stageable}s to the types that created them.
         *
         * @param typeMapper container to map {@link Stageable}s to types.
         */
        MatcherBinding mappingWith(StageableTypeMapper typeMapper);
    }

    /**
     * Builder pattern helper.
     */
    private static class BindingBuilder<A extends Annotation> implements MapperBinding {

        private Matcher<? super TypeLiteral<?>> typeMatcher = any();

        private final Stager<A> stager;

        private StageableTypeMapper typeMapper = new NoOpStageableTypeMapper();

        public BindingBuilder(Stager<A> stager) {
            this.stager = stager;
        }

        @Override
        public MatcherBinding mappingWith(StageableTypeMapper typeMapper) {
            this.typeMapper = checkNotNull(typeMapper, "Argument 'typeMapper' must be not null.");
            return this;
        }

        @Override
        public void matching(Matcher<? super TypeLiteral<?>> typeMatcher) {
            this.typeMatcher = checkNotNull(typeMatcher, "Argument 'typeMatcher' must be not null");
        }

    }

    private static <T> T checkNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}
