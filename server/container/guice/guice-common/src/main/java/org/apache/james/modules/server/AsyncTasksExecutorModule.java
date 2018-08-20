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
package org.apache.james.modules.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Names;

public class AsyncTasksExecutorModule extends AbstractModule {

    private static final int THREAD_POOL_SIZE = 8;

    @Override
    protected void configure() {
        bind(ExecutorService.class).annotatedWith(Names.named("AsyncExecutor"))
            .toProvider(new LifecycleAwareExecutorServiceProvider(
                Executors.newFixedThreadPool(THREAD_POOL_SIZE)));
    }

    public static class LifecycleAwareExecutorServiceProvider implements Provider<ExecutorService> {
        private final ExecutorService executorService;

        LifecycleAwareExecutorServiceProvider(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public ExecutorService get() {
            return executorService;
        }

        @PreDestroy
        public void stop() {
            executorService.shutdownNow();
        }
    }
}
