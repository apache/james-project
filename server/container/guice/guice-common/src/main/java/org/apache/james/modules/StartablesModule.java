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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.Startables;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;

public class StartablesModule extends AbstractModule {

    private final Startables startables;

    public StartablesModule() {
        startables = new Startables();
    }

    @Override
    protected void configure() {
        bind(Startables.class).toInstance(startables);

        bindListener(new SubclassesOf(Startable.class), this::hear);
    }

    private <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        InjectionListener<? super I> injectionListener = ignored -> startables.add(type.getRawType().asSubclass(Startable.class));
        encounter.register(injectionListener);
    }

    private static class SubclassesOf extends AbstractMatcher<TypeLiteral<?>> implements Serializable {

        private final Class<?> superclass;

        private SubclassesOf(Class<?> superclass) {
            this.superclass = checkNotNull(superclass, "superclass");
        }

        @Override
        public boolean matches(TypeLiteral<?> type) {
            return superclass.isAssignableFrom(type.getRawType());
        }
    }
}
