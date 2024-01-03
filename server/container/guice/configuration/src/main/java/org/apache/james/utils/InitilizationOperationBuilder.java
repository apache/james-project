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

import static org.apache.james.utils.InitializationOperation.DEFAULT_PRIORITY;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;

import com.google.common.collect.ImmutableList;

public class InitilizationOperationBuilder {

    @FunctionalInterface
    public interface Init {
        void init() throws Exception;
    }

    @FunctionalInterface
    public interface RequireInit {
        PrivateImpl init(Init init);
    }

    public static RequireInit forClass(Class<? extends Startable> type) {
        return init -> new PrivateImpl(init, type, DEFAULT_PRIORITY);
    }

    public static RequireInit forClass(Class<? extends Startable> type, int priority) {
        return init -> new PrivateImpl(init, type, priority);
    }

    public static class PrivateImpl implements InitializationOperation {
        private final Init init;
        private final Class<? extends Startable> type;
        private List<Class<?>> requires;

        private final int priority;

        private PrivateImpl(Init init, Class<? extends Startable> type, int priority) {
            this.init = init;
            this.type = type;
            this.priority = priority;
            /*
            Class requirements are by default infered from the parameters of the first @Inject annotated constructor.

            If it does not exist, use the first constructor (case in a @Provides).
             */
            this.requires = Arrays.stream(type.getDeclaredConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst()
                .or(() -> Arrays.stream(type.getDeclaredConstructors()).findFirst())
                .stream()
                .flatMap(c -> Arrays.stream(c.getParameterTypes()))
                .collect(ImmutableList.toImmutableList());
        }

        @Override
        public void initModule() throws Exception {
            init.init();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return type;
        }

        public PrivateImpl requires(List<Class<?>> requires) {
            this.requires = requires;
            return this;
        }

        @Override
        public List<Class<?>> requires() {
            return requires;
        }

        @Override
        public int priority() {
            return priority;
        }
    }
}
