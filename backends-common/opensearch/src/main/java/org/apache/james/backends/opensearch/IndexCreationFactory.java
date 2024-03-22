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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.WaitForActiveShards;
import org.opensearch.client.opensearch._types.analysis.Analyzer;
import org.opensearch.client.opensearch._types.analysis.CustomAnalyzer;
import org.opensearch.client.opensearch._types.analysis.CustomNormalizer;
import org.opensearch.client.opensearch._types.analysis.Normalizer;
import org.opensearch.client.opensearch._types.analysis.SnowballLanguage;
import org.opensearch.client.opensearch._types.analysis.SnowballTokenFilter;
import org.opensearch.client.opensearch._types.analysis.Tokenizer;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsAliasRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettingsAnalysis;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.opensearch.client.opensearch.indices.update_aliases.AddAction;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class IndexCreationFactory {

    public static class IndexCreationCustomAnalyzer {
        private final String key;
        private final Analyzer analyzer;

        public IndexCreationCustomAnalyzer(String key, Analyzer analyzer) {
            this.key = key;
            this.analyzer = analyzer;
        }

        public String getKey() {
            return key;
        }

        public Analyzer getAnalyzer() {
            return analyzer;
        }
    }

    public static class IndexCreationCustomTokenizer {
        private final String key;
        private final Tokenizer tokenizer;

        public IndexCreationCustomTokenizer(String key, Tokenizer tokenizer) {
            this.key = key;
            this.tokenizer = tokenizer;
        }

        public String getKey() {
            return key;
        }

        public Tokenizer getTokenizer() {
            return tokenizer;
        }
    }

    static class IndexCreationPerformer {
        public static class Builder {

            @FunctionalInterface
            public interface RequireNbShards {
                RequireNbReplica nbShards(int nbShards);
            }

            @FunctionalInterface
            public interface RequireNbReplica {
                RequireWaitForActiveShards nbReplica(int nbReplica);
            }

            @FunctionalInterface
            public interface RequireWaitForActiveShards {
                RequireIndexName waitForActiveShards(int waitForActiveShards);
            }

            @FunctionalInterface
            public interface RequireIndexName {
                FinalStage indexName(IndexName indexName);
            }

            public static class FinalStage {
                private final int nbShards;
                private final int nbReplica;
                private final int waitForActiveShards;
                private final IndexName indexName;
                private final ImmutableList.Builder<AliasName> aliases;
                private final ImmutableList.Builder<IndexCreationCustomAnalyzer> customAnalyzers;
                private final ImmutableList.Builder<IndexCreationCustomTokenizer> customTokenizers;

                FinalStage(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName) {
                    this.nbShards = nbShards;
                    this.nbReplica = nbReplica;
                    this.waitForActiveShards = waitForActiveShards;
                    this.indexName = indexName;
                    this.aliases = ImmutableList.builder();
                    this.customAnalyzers = ImmutableList.builder();
                    this.customTokenizers = ImmutableList.builder();
                }

                public FinalStage addAlias(AliasName... aliases) {
                    this.aliases.add(aliases);
                    return this;
                }

                public FinalStage addAlias(Collection<AliasName> aliases) {
                    this.aliases.addAll(ImmutableList.copyOf(aliases));
                    return this;
                }

                public FinalStage customAnalyzers(IndexCreationCustomAnalyzer... customAnalyzers) {
                    this.customAnalyzers.add(customAnalyzers);
                    return this;
                }

                public FinalStage customTokenizers(IndexCreationCustomTokenizer... customTokenizers) {
                    this.customTokenizers.add(customTokenizers);
                    return this;
                }

                public IndexCreationPerformer build() {
                    return new IndexCreationPerformer(nbShards, nbReplica, waitForActiveShards, indexName, aliases.build(), customAnalyzers.build(), customTokenizers.build());
                }

                public ReactorOpenSearchClient createIndexAndAliases(ReactorOpenSearchClient client) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.empty());
                }

                public ReactorOpenSearchClient createIndexAndAliases(ReactorOpenSearchClient client, TypeMapping mappingContent) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.of(mappingContent));
                }

                public ReactorOpenSearchClient createIndexAndAliases(ReactorOpenSearchClient client, Optional<IndexSettings> indexSettings, Optional<TypeMapping> mappingContent) {
                    return build().createIndexAndAliases(client, indexSettings, mappingContent);
                }
            }
        }

        public static Builder.RequireNbShards builder() {
            return nbShards -> nbReplica -> waitForActiveShards -> indexName
                -> new Builder.FinalStage(nbShards, nbReplica, waitForActiveShards, indexName);
        }

        private final int nbShards;
        private final int nbReplica;
        private final int waitForActiveShards;
        private final IndexName indexName;
        private final ImmutableList<AliasName> aliases;
        private final ImmutableList<IndexCreationCustomAnalyzer> customAnalyzers;
        private final ImmutableList<IndexCreationCustomTokenizer> customTokenizers;

        private IndexCreationPerformer(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName,
                                       ImmutableList<AliasName> aliases, ImmutableList<IndexCreationCustomAnalyzer> customAnalyzers,
                                       ImmutableList<IndexCreationCustomTokenizer> customTokenizers) {
            this.nbShards = nbShards;
            this.nbReplica = nbReplica;
            this.waitForActiveShards = waitForActiveShards;
            this.indexName = indexName;
            this.aliases = aliases;
            this.customAnalyzers = customAnalyzers;
            this.customTokenizers = customTokenizers;
        }

        public ReactorOpenSearchClient createIndexAndAliases(ReactorOpenSearchClient client,
                                                                Optional<IndexSettings> indexSettings,
                                                                Optional<TypeMapping> mappingContent) {
            Preconditions.checkNotNull(indexName);
            try {
                createIndexIfNeeded(client, indexName, indexSettings.orElse(generateSetting()), mappingContent);
                aliases.forEach(Throwing.<AliasName>consumer(alias -> createAliasIfNeeded(client, indexName, alias))
                    .sneakyThrow());
            } catch (IOException e) {
                LOGGER.error("Error while creating index : ", e);
            }
            return client;
        }

        private void createAliasIfNeeded(ReactorOpenSearchClient client, IndexName indexName, AliasName aliasName) throws IOException {
            if (!aliasExist(client, aliasName)) {
                client.updateAliases(
                    new UpdateAliasesRequest.Builder()
                        .actions(new Action.Builder()
                            .add(new AddAction.Builder()
                                .index(indexName.getValue())
                                .alias(aliasName.getValue())
                                .build())
                            .build())
                        .build())
                    .block();
            }
        }

        private boolean aliasExist(ReactorOpenSearchClient client, AliasName aliasName) throws IOException {
            return client.aliasExists(new ExistsAliasRequest.Builder()
                    .name(aliasName.getValue())
                    .build())
                .map(BooleanResponse::value)
                .block();
        }

        private void createIndexIfNeeded(ReactorOpenSearchClient client, IndexName indexName, IndexSettings settings, Optional<TypeMapping> mappingContent) throws IOException {
            try {
                if (!indexExists(client, indexName)) {
                    CreateIndexRequest.Builder request = new CreateIndexRequest.Builder()
                        .index(indexName.getValue())
                        .waitForActiveShards(new WaitForActiveShards.Builder()
                            .count(waitForActiveShards)
                            .build())
                        .settings(settings);
                    mappingContent.ifPresent(request::mappings);
                    client.createIndex(request.build())
                        .block();
                }
            } catch (OpenSearchException exception) {
                if (exception.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE)) {
                    LOGGER.info("Index [{}] already exists", indexName.getValue());
                } else {
                    throw exception;
                }
            }
        }

        private boolean indexExists(ReactorOpenSearchClient client, IndexName indexName) throws IOException {
            return client.indexExists(new ExistsRequest.Builder()
                    .index(indexName.getValue())
                    .build())
                .map(BooleanResponse::value)
                .block();
        }

        private IndexSettings generateSetting() {
            return new IndexSettings.Builder()
                .numberOfShards(Integer.toString(nbShards))
                .numberOfReplicas(Integer.toString(nbReplica))
                .analysis(new IndexSettingsAnalysis.Builder()
                    .normalizer(CASE_INSENSITIVE, new Normalizer.Builder()
                        .custom(generateNormalizer())
                        .build())
                    .analyzer(generateAnalyzers())
                    .tokenizer(generateTokenizers())
                    .build())
                .build();
        }

        private CustomNormalizer generateNormalizer() {
            return new CustomNormalizer.Builder()
                .filter("lowercase", "asciifolding")
                .build();
        }

        private SnowballTokenFilter generateFilter() {
            return new SnowballTokenFilter.Builder()
                .language(SnowballLanguage.English)
                .build();
        }

        private Map<String, Analyzer> defaultAnalyzers() {
            return ImmutableMap.of(
                KEEP_MAIL_AND_URL, new Analyzer.Builder().custom(
                    new CustomAnalyzer.Builder()
                        .tokenizer("uax_url_email")
                        .filter("lowercase", "stop")
                        .build())
                    .build()
            );
        }

        private Map<String, Analyzer> generateAnalyzers() {
            if (customAnalyzers.isEmpty()) {
                return defaultAnalyzers();
            }
            return customAnalyzers.stream()
                .collect(Collectors.toMap(
                    IndexCreationCustomAnalyzer::getKey,
                    IndexCreationCustomAnalyzer::getAnalyzer));
        }

        private Map<String, Tokenizer> generateTokenizers() {
            return customTokenizers.stream()
                .collect(Collectors.toMap(
                    IndexCreationCustomTokenizer::getKey,
                    IndexCreationCustomTokenizer::getTokenizer
                ));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);
    private static final String INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE = "type=resource_already_exists_exception";

    private final int nbShards;
    private final int nbReplica;
    private final int waitForActiveShards;

    public static final String CASE_INSENSITIVE = "case_insensitive";
    public static final String KEEP_MAIL_AND_URL = "keep_mail_and_url";
    public static final String BOOLEAN = "boolean";
    public static final String TYPE = "type";
    public static final String LONG = "long";
    public static final String DOUBLE = "double";
    public static final String KEYWORD = "keyword";
    public static final String PROPERTIES = "properties";
    public static final String ROUTING = "_routing";
    public static final String REQUIRED = "required";
    public static final String DATE = "date";
    public static final String FORMAT = "format";
    public static final String NESTED = "nested";
    public static final String FIELDS = "fields";
    public static final String RAW = "raw";
    public static final String ANALYZER = "analyzer";
    public static final String TOKENIZER = "tokenizer";
    public static final String NORMALIZER = "normalizer";
    public static final String SEARCH_ANALYZER = "search_analyzer";

    @Inject
    public IndexCreationFactory(OpenSearchConfiguration configuration) {
        this.nbShards = configuration.getNbShards();
        this.nbReplica = configuration.getNbReplica();
        this.waitForActiveShards = configuration.getWaitForActiveShards();
    }

    public IndexCreationPerformer.Builder.FinalStage useIndex(IndexName indexName) {
        Preconditions.checkNotNull(indexName);
        return IndexCreationPerformer.builder()
            .nbShards(nbShards)
            .nbReplica(nbReplica)
            .waitForActiveShards(waitForActiveShards)
            .indexName(indexName);
    }
}
