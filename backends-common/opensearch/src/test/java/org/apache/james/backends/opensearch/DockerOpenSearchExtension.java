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

package org.apache.james.backends.opensearch;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.core.SearchRequest;

public class DockerOpenSearchExtension implements AfterEachCallback, BeforeEachCallback, ParameterResolver {

    @FunctionalInterface
    public interface CleanupStrategy {
        CleanupStrategy NONE = any -> {
        };

        void clean(DockerOpenSearch openSearch);
    }

    public static class DefaultCleanupStrategy implements CleanupStrategy {
        @Override
        public void clean(DockerOpenSearch openSearch) {
            openSearch.cleanUpData();
        }
    }

    public static class DeleteAllIndexDocumentsCleanupStrategy implements CleanupStrategy {
        private final WriteAliasName aliasName;
        private ReactorOpenSearchClient client;

        public DeleteAllIndexDocumentsCleanupStrategy(WriteAliasName aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public void clean(DockerOpenSearch openSearch) {
            Awaitility.await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    openSearch.flushIndices();
                    ReactorOpenSearchClient client = client(openSearch);
                    new DeleteByQueryPerformer(client, aliasName)
                        .perform(new MatchAllQuery.Builder().build().toQuery())
                        .block();
                    SearchRequest searchRequest = new SearchRequest.Builder()
                        .query(new MatchAllQuery.Builder().build().toQuery())
                        .build();
                    openSearch.flushIndices();

                    int currentHits = client.search(searchRequest)
                        .map(response -> response.hits().hits())
                        .flatMapIterable(e -> e)
                        .filter(hits -> !hits.index().equalsIgnoreCase(".plugins-ml-config")) // it was added by default from opensearch 2.9.0
                        .collectList()
                        .block().size();

                    return currentHits == 0;
                });
        }

        private ReactorOpenSearchClient client(DockerOpenSearch openSearch) {
            if (client == null) {
                client = openSearch.clientProvider().get();
            }
            return client;
        }
    }

    private final DockerOpenSearch openSearch = DockerOpenSearchSingleton.INSTANCE;
    private final CleanupStrategy cleanupStrategy;

    public DockerOpenSearchExtension() {
        this.cleanupStrategy = new DefaultCleanupStrategy();
    }

    public DockerOpenSearchExtension(CleanupStrategy cleanupStrategy) {
        this.cleanupStrategy = cleanupStrategy;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cleanupStrategy.clean(openSearch);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!openSearch.isRunning()) {
            openSearch.unpause();
        }
        awaitForOpenSearch();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenSearch.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return openSearch;
    }

    public void awaitForOpenSearch() {
        openSearch.flushIndices();
    }

    public DockerOpenSearch getDockerOpenSearch() {
        return openSearch;
    }
}
