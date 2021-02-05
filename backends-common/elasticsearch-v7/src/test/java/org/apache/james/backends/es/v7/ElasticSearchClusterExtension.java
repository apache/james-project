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

package org.apache.james.backends.es.v7;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.util.Host;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ElasticSearchClusterExtension implements AfterAllCallback, BeforeAllCallback, AfterEachCallback, ParameterResolver {

    static class ElasticSearchCluster {
        DockerElasticSearch es1;
        DockerElasticSearch es2;

        ElasticSearchCluster(DockerElasticSearch es1, DockerElasticSearch es2) {
            this.es1 = es1;
            this.es2 = es2;
        }

        void start() {
            doInParallel(es1::start, es2::start);
        }

        void cleanUp() {
            doInParallel(() -> {
                    if (es1.isRunning()) {
                        es1.cleanUpData();
                    }
                },
                () -> {
                    if (es2.isRunning()) {
                        es2.cleanUpData();
                    }
                });
        }

        void stop() {
            doInParallel(es2::stop);
        }

        List<Host> getHosts() {
            return ImmutableList.of(es1.getHttpHost(), es2.getHttpHost());
        }

        private void doInParallel(Runnable...runnables) {
            Flux.fromStream(Stream.of(runnables)
                    .map(Mono::fromRunnable))
                .parallel(runnables.length)
                .runOn(Schedulers.elastic())
                .flatMap(Function.identity())
                .then()
                .block();
        }
    }
    
    private final ElasticSearchCluster esCluster;

    ElasticSearchClusterExtension(ElasticSearchCluster esCluster) {
        this.esCluster = esCluster;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        esCluster.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        esCluster.cleanUp();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        esCluster.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == ElasticSearchCluster.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return esCluster;
    }
}