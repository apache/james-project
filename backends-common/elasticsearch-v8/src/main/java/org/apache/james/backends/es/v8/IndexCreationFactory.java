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

package org.apache.james.backends.es.v8;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.WaitForActiveShards;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.Normalizer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilterDefinition;
import co.elastic.clients.elasticsearch._types.analysis.Tokenizer;
import co.elastic.clients.elasticsearch._types.analysis.TokenizerDefinition;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.elasticsearch.indices.update_aliases.AddAction;
import co.elastic.clients.transport.endpoints.BooleanResponse;

public class IndexCreationFactory {

    public static class IndexCreationCustomElement {
        public static IndexCreationCustomElement from(String key, String value) {
            try {
                new ObjectMapper().readTree(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("value must be a valid json");
            }
            return new IndexCreationCustomElement(key, value);
        }

        private final String key;
        private final String payload;

        IndexCreationCustomElement(String key, String payload) {
            this.key = key;
            this.payload = payload;
        }

        public String getKey() {
            return key;
        }

        public String getPayload() {
            return payload;
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
                private final ImmutableList.Builder<IndexCreationCustomElement> customAnalyzers;
                private final ImmutableList.Builder<IndexCreationCustomElement> customTokenizers;

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

                public FinalStage customAnalyzers(IndexCreationCustomElement... customAnalyzers) {
                    this.customAnalyzers.add(customAnalyzers);
                    return this;
                }

                public FinalStage customTokenizers(IndexCreationCustomElement... customTokenizers) {
                    this.customTokenizers.add(customTokenizers);
                    return this;
                }

                public IndexCreationPerformer build() {
                    return new IndexCreationPerformer(nbShards, nbReplica, waitForActiveShards, indexName, aliases.build(), customAnalyzers.build(), customTokenizers.build());
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.empty());
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, TypeMapping mappingContent) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.of(mappingContent));
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, Optional<IndexSettings> indexSettings, Optional<TypeMapping> mappingContent) {
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
        private final ImmutableList<IndexCreationCustomElement> customAnalyzers;
        private final ImmutableList<IndexCreationCustomElement> customTokenizers;

        private IndexCreationPerformer(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName,
                                       ImmutableList<AliasName> aliases, ImmutableList<IndexCreationCustomElement> customAnalyzers,
                                       ImmutableList<IndexCreationCustomElement> customTokenizers) {
            this.nbShards = nbShards;
            this.nbReplica = nbReplica;
            this.waitForActiveShards = waitForActiveShards;
            this.indexName = indexName;
            this.aliases = aliases;
            this.customAnalyzers = customAnalyzers;
            this.customTokenizers = customTokenizers;
        }

        public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client,
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

        private void createAliasIfNeeded(ReactorElasticSearchClient client, IndexName indexName, AliasName aliasName) throws IOException {
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

        private boolean aliasExist(ReactorElasticSearchClient client, AliasName aliasName) {
            return client.aliasExists(new ExistsAliasRequest.Builder()
                    .name(aliasName.getValue())
                    .build())
                .map(BooleanResponse::value)
                .block();
        }

        private void createIndexIfNeeded(ReactorElasticSearchClient client, IndexName indexName, IndexSettings settings, Optional<TypeMapping> mappingContent) throws IOException {
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
            } catch (ElasticsearchException exception) {
                if (exception.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE)) {
                    LOGGER.info("Index [{}] already exists", indexName.getValue());
                } else {
                    throw exception;
                }
            }
        }

        private boolean indexExists(ReactorElasticSearchClient client, IndexName indexName) {
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
                        .withJson(generateNormalizer())
                        .build())
                    .analyzer(generateAnalyzers())
                    .tokenizer(generateTokenizers())
                    .filter(ENGLISH_SNOWBALL, new TokenFilter.Builder()
                        .definition(new TokenFilterDefinition.Builder()
                            .withJson(generateFilter())
                            .build())
                        .build())
                    .build())
                .build();
        }

        private Reader generateNormalizer() {
            return new StringReader(
                "{'type': 'custom', 'char_filter': [], 'filter': ['lowercase', 'asciifolding']}"
                .replace('\'', '"'));
        }

        private Reader generateFilter() {
            return new StringReader(
                "{'type': 'snowball', 'language': 'English'}"
                    .replace('\'', '"'));
        }

        private Map<String, Analyzer> defaultAnalyzers() {
            return ImmutableMap.of(
                KEEP_MAIL_AND_URL, generateAnalyzer("{'type': 'custom', 'tokenizer': 'uax_url_email', 'filter': ['lowercase', 'stop']}".replace('\'', '"')),
                SNOWBALL_KEEP_MAIL_AND_URL, generateAnalyzer(("{'type': 'custom', 'tokenizer': 'uax_url_email', 'filter': ['lowercase', 'stop', '" + ENGLISH_SNOWBALL + "']}").replace('\'', '"'))
            );
        }

        private Map<String, Analyzer> generateAnalyzers() {
            if (customAnalyzers.isEmpty()) {
                return defaultAnalyzers();
            }
            return customAnalyzers.stream()
                .collect(Collectors.toMap(
                    IndexCreationCustomElement::getKey,
                    analyzer -> generateAnalyzer(analyzer.getPayload())));
        }

        private Analyzer generateAnalyzer(String payload) {
            return new Analyzer.Builder()
                .withJson(new StringReader(payload))
                .build();
        }

        private Map<String, Tokenizer> generateTokenizers() {
            return customTokenizers.stream()
                .collect(Collectors.toMap(
                    IndexCreationCustomElement::getKey,
                    tokenizer -> generateTokenizer(tokenizer.getPayload())
                ));
        }

        private Tokenizer generateTokenizer(String payload) {
            return new Tokenizer.Builder()
                .definition(new TokenizerDefinition.Builder()
                    .withJson(new StringReader(payload))
                    .build())
                .build();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCreationFactory.class);
    private static final String INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE = "type=resource_already_exists_exception";

    private final int nbShards;
    private final int nbReplica;
    private final int waitForActiveShards;

    public static final String CASE_INSENSITIVE = "case_insensitive";
    public static final String KEEP_MAIL_AND_URL = "keep_mail_and_url";
    public static final String SNOWBALL_KEEP_MAIL_AND_URL = "snowball_keep_mail_and_token";
    public static final String ENGLISH_SNOWBALL = "english_snowball";
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
    public IndexCreationFactory(ElasticSearchConfiguration configuration) {
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
