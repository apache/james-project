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
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;

/**
 * Guice module to register methods to be invoked after injection is complete.
 */
public abstract class LifeCycleModule extends AbstractModule {

    /**
     * Binds lifecycle listener.
     *
     * @param annotation the lifecycle annotation to be searched.
     */
    protected final void bindLifeCycle(Class<? extends Annotation> annotation) {
        bindLifeCycle(annotation, any());
    }

    /**
     * Binds lifecycle listener.
     *
     * @param annotation  the lifecycle annotation to be searched.
     * @param typeMatcher the filter for injectee types.
     */
    protected final void bindLifeCycle(Class<? extends Annotation> annotation, Matcher<? super TypeLiteral<?>> typeMatcher) {
        bindLifeCycle(asList(annotation), typeMatcher);
    }

    /**
     * Binds lifecycle listener.
     *
     * @param annotations the lifecycle annotations to be searched in the order to be searched.
     * @param typeMatcher the filter for injectee types.
     */
    protected final void bindLifeCycle(List<? extends Class<? extends Annotation>> annotations, Matcher<? super TypeLiteral<?>> typeMatcher) {
        bindListener(typeMatcher, new AbstractMethodTypeListener(annotations) {
            @Override
            protected <I> void hear(final Method method, TypeLiteral<I> parentType, TypeEncounter<I> encounter, final Class<? extends Annotation> annotationType) {
                encounter.register((InjectionListener<I>) injectee -> {
                    try {
                        method.invoke(injectee);
                    } catch (IllegalArgumentException e) {
                        // should not happen, anyway...
                        throw new ProvisionException(format("Method @%s %s requires arguments", annotationType.getName(), method), e);
                    } catch (IllegalAccessException e) {
                        throw new ProvisionException(String.format("Impossible to access to @%s %s on %s", annotationType.getName(), method, injectee), e);
                    } catch (InvocationTargetException e) {
                        throw new ProvisionException(
                            String.format("An error occurred while invoking @%s %s on %s", annotationType.getName(), method, injectee), e.getCause());
                    }
                });
            }
        });
    }

}
