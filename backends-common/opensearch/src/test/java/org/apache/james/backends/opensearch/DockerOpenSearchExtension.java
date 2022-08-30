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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class DockerOpenSearchExtension implements AfterEachCallback, BeforeEachCallback, ParameterResolver {

    @FunctionalInterface
    interface CleanupStrategy {
        CleanupStrategy NONE = any -> {};

        void clean(DockerOpenSearch elasticSearch);
    }

    public static class DefaultCleanupStrategy implements CleanupStrategy {
        @Override
        public void clean(DockerOpenSearch elasticSearch) {
            elasticSearch.cleanUpData();
        }
    }

    public static class DeleteAllIndexDocumentsCleanupStrategy implements CleanupStrategy {
        private final WriteAliasName aliasName;

        public DeleteAllIndexDocumentsCleanupStrategy(WriteAliasName aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public void clean(DockerOpenSearch elasticSearch) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            elasticSearch.flushIndices();
            ReactorOpenSearchClient client = elasticSearch.clientProvider().get();
            new DeleteByQueryPerformer(client, aliasName)
                .perform(QueryBuilders.matchAllQuery())
                .block();

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.source(new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery()));
            Awaitility.await()
                .until(() -> client.search(new SearchRequest(searchRequest), RequestOptions.DEFAULT)
                    .map(searchResponse -> searchResponse.getHits().getHits().length)
                    .block() == 0);
            try {
                client.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final DockerOpenSearch elasticSearch = DockerOpenSearchSingleton.INSTANCE;
    private final CleanupStrategy cleanupStrategy;

    public DockerOpenSearchExtension() {
        this.cleanupStrategy = new DefaultCleanupStrategy();
    }

    public DockerOpenSearchExtension(CleanupStrategy cleanupStrategy) {
        this.cleanupStrategy = cleanupStrategy;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cleanupStrategy.clean(elasticSearch);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (!elasticSearch.isRunning()) {
            elasticSearch.unpause();
        }
        awaitForOpenSearch();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenSearch.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return elasticSearch;
    }

    public void awaitForOpenSearch() {
        elasticSearch.flushIndices();
    }

    public DockerOpenSearch getDockerOpenSearch() {
        return elasticSearch;
    }
}
