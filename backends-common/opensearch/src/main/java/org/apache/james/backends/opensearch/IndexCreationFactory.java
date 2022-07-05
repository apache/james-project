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

import static org.opensearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class IndexCreationFactory {

    public static class IndexCreationCustomElement {
        public static IndexCreationCustomElement EMPTY = from("{}");

        public static IndexCreationCustomElement from(String value) {
            try {
                new ObjectMapper().readTree(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("value must be a valid json");
            }
            return new IndexCreationCustomElement(value);
        }

        private final String payload;

        IndexCreationCustomElement(String payload) {
            this.payload = payload;
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
                private Optional<IndexCreationCustomElement> customAnalyzers;
                private Optional<IndexCreationCustomElement> customTokenizers;

                FinalStage(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName) {
                    this.nbShards = nbShards;
                    this.nbReplica = nbReplica;
                    this.waitForActiveShards = waitForActiveShards;
                    this.indexName = indexName;
                    this.aliases = ImmutableList.builder();
                    this.customAnalyzers = Optional.empty();
                    this.customTokenizers = Optional.empty();
                }

                public FinalStage addAlias(AliasName... aliases) {
                    this.aliases.add(aliases);
                    return this;
                }

                public FinalStage addAlias(Collection<AliasName> aliases) {
                    this.aliases.addAll(ImmutableList.copyOf(aliases));
                    return this;
                }

                public FinalStage customAnalyzers(IndexCreationCustomElement customAnalyzers) {
                    this.customAnalyzers = Optional.of(customAnalyzers);
                    return this;
                }

                public FinalStage customTokenizers(IndexCreationCustomElement customTokenizers) {
                    this.customTokenizers = Optional.of(customTokenizers);
                    return this;
                }

                public IndexCreationPerformer build() {
                    return new IndexCreationPerformer(nbShards, nbReplica, waitForActiveShards, indexName, aliases.build(), customAnalyzers, customTokenizers);
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.empty());
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, XContentBuilder mappingContent) {
                    return build().createIndexAndAliases(client, Optional.empty(), Optional.of(mappingContent));
                }

                public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, Optional<XContentBuilder> indexSettings, Optional<XContentBuilder> mappingContent) {
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
        private final Optional<IndexCreationCustomElement> customAnalyzers;
        private final Optional<IndexCreationCustomElement> customTokenizers;

        private IndexCreationPerformer(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName, ImmutableList<AliasName> aliases,
                                      Optional<IndexCreationCustomElement> customAnalyzers, Optional<IndexCreationCustomElement> customTokenizers) {
            this.nbShards = nbShards;
            this.nbReplica = nbReplica;
            this.waitForActiveShards = waitForActiveShards;
            this.indexName = indexName;
            this.aliases = aliases;
            this.customAnalyzers = customAnalyzers;
            this.customTokenizers = customTokenizers;
        }

        public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, Optional<XContentBuilder> indexSettings,
                                                                Optional<XContentBuilder> mappingContent) {
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
                client.indices()
                    .updateAliases(
                        new IndicesAliasesRequest().addAliasAction(
                            new AliasActions(AliasActions.Type.ADD)
                                .index(indexName.getValue())
                                .alias(aliasName.getValue())),
                        RequestOptions.DEFAULT);
            }
        }

        private boolean aliasExist(ReactorElasticSearchClient client, AliasName aliasName) throws IOException {
            return client.indices()
                .existsAlias(new GetAliasesRequest().aliases(aliasName.getValue()), RequestOptions.DEFAULT);
        }

        private void createIndexIfNeeded(ReactorElasticSearchClient client, IndexName indexName, XContentBuilder settings, Optional<XContentBuilder> mappingContent) throws IOException {
            try {
                if (!indexExists(client, indexName)) {
                    CreateIndexRequest request = new CreateIndexRequest(indexName.getValue()).source(settings);
                    mappingContent.ifPresent(request::mapping);
                    client.indices().create(
                        request,
                        RequestOptions.DEFAULT);
                }
            } catch (OpenSearchStatusException exception) {
                if (exception.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE)) {
                    LOGGER.info("Index [{}] already exists", indexName.getValue());
                } else {
                    throw exception;
                }
            }
        }

        private boolean indexExists(ReactorElasticSearchClient client, IndexName indexName) throws IOException {
            return client.indices().exists(new GetIndexRequest(indexName.getValue()), RequestOptions.DEFAULT);
        }

        private XContentBuilder generateSetting() throws IOException {
            return jsonBuilder()
                .startObject()
                    .startObject("settings")
                        .field("number_of_shards", nbShards)
                        .field("number_of_replicas", nbReplica)
                        .field("index.write.wait_for_active_shards", waitForActiveShards)
                        .startObject("analysis")
                            .startObject("normalizer")
                                .startObject(CASE_INSENSITIVE)
                                    .field("type", "custom")
                                    .startArray("char_filter")
                                    .endArray()
                                    .startArray("filter")
                                        .value("lowercase")
                                        .value("asciifolding")
                                    .endArray()
                                .endObject()
                            .endObject()
                            .rawField(ANALYZER, generateAnalyzers(), XContentType.JSON)
                            .rawField(TOKENIZER, generateTokenizer(), XContentType.JSON)
                        .endObject()
                    .endObject()
                .endObject();
        }

        private String analyzerDefault() throws IOException {
            XContentBuilder analyzerBuilder = jsonBuilder()
                .startObject()
                    .startObject(KEEP_MAIL_AND_URL)
                        .field("tokenizer", "uax_url_email")
                        .startArray("filter")
                            .value("lowercase")
                            .value("stop")
                        .endArray()
                    .endObject()
                .endObject();

            return Strings.toString(analyzerBuilder);
        }

        private InputStream generateAnalyzers() {
            return new ByteArrayInputStream(customAnalyzers.orElseGet(Throwing.supplier(() -> IndexCreationCustomElement.from(analyzerDefault())).sneakyThrow())
                .getPayload()
                .getBytes(StandardCharsets.UTF_8));
        }

        private InputStream generateTokenizer() {
            return new ByteArrayInputStream(customTokenizers.orElse(IndexCreationCustomElement.EMPTY)
                .getPayload()
                .getBytes(StandardCharsets.UTF_8));
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
