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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

import org.apache.james.backends.es.v8.IndexCreationFactory.IndexCreationCustomElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilterDefinition;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.json.JsonpMappingException;

class IndexCreationFactoryTest {
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

    public static IndexSettings getValidIndexSetting() {
        return new IndexSettings.Builder()
            .index(new IndexSettings.Builder()
                .maxNgramDiff(10)
                .build())
            .analysis(new IndexSettingsAnalysis.Builder()
                .analyzer("email_ngram_filter_analyzer", new Analyzer.Builder()
                    .withJson(generateAnalyzer())
                    .build())
                .filter("ngram_filter", new TokenFilter.Builder()
                    .definition(new TokenFilterDefinition.Builder()
                        .withJson(generateFilter())
                        .build())
                    .build())
                .build())
            .build();
    }

    public static IndexSettings getInvalidIndexSetting() {
        return new IndexSettings.Builder()
            .analysis(new IndexSettingsAnalysis.Builder()
                .analyzer("email_ngram_filter_analyzer", new Analyzer.Builder()
                    .withJson(generateAnalyzer())
                    .build())
                .filter("ngram_filter", new TokenFilter.Builder()
                    .definition(new TokenFilterDefinition.Builder()
                        .withJson(generateFilter())
                        .build())
                    .build())
                .build())
            .build();
    }

    private static Reader generateAnalyzer() {
        return new StringReader(
            ("{" +
             "  'type': 'custom'," +
             "  'tokenizer': 'uax_url_email'," +
             "  'filter': ['ngram_filter']" +
             "}")
            .replace('\'', '"'));
    }

    private static Reader generateFilter() {
        return new StringReader(
            ("{" +
             "  'type': 'ngram'," +
             "  'min_gram': 3," +
             "  'max_gram': 13" +
             "}")
            .replace('\'', '"'));
    }

    @RegisterExtension
    public DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();
    private ReactorElasticSearchClient client;

    @BeforeEach
    void setUp() {
        client = elasticSearch.getDockerElasticSearch().clientProvider().get();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void createIndexAndAliasShouldNotThrowWhenCalledSeveralTime() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);

        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);
    }

    @Test
    void useIndexShouldThrowWhenNull() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void customAnalyzerShouldNotThrowWhenValidAnalyzer() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customAnalyzers(IndexCreationCustomElement.from("my_custom_analyzer", "{" +
                "    \"type\": \"custom\"," +
                "    \"tokenizer\": \"standard\"," +
                "    \"char_filter\": [" +
                "        \"html_strip\"" +
                "    ]," +
                "    \"filter\": [" +
                "        \"lowercase\"," +
                "        \"asciifolding\"" +
                "    ]" +
                "}"))
            .createIndexAndAliases(client);
    }

    @Test
    void customAnalyzerShouldThrowWhenInValidAnalyzer() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customAnalyzers(IndexCreationCustomElement.from("my_custom_analyzer", "{" +
                    "    \"type\": \"invalid\"," +
                    "    \"tokenizer\": \"not_Found_tokenizer\"" +
                    "}"))
                .createIndexAndAliases(client))
            .isInstanceOf(JsonpMappingException.class);
    }

    @Test
    void customTokenizersShouldNotThrowWhenValidTokenizers() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customTokenizers(IndexCreationCustomElement.from("custom_tokenizer", "{" +
                "    \"type\": \"pattern\"," +
                "    \"pattern\": \"[ .,!?]\"," +
                "    \"flags\": \"CASE_INSENSITIVE|COMMENTS\"," +
                "    \"group\": 0" +
                "}"))
            .createIndexAndAliases(client);
    }

    @Test
    void customTokenizersShouldThrowWhenInValidTokenizers() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customTokenizers(IndexCreationCustomElement.from("custom_tokenizer", "{" +
                    "    \"type\": \"invalidType\"," +
                    "    \"pattern\": \"[ .,!?]\"" +
                    "}"))
                .createIndexAndAliases(client))
            .isInstanceOf(JsonpMappingException.class);
    }

    @Test
    void createIndexShouldNotThrowWhenProvidedValidCustomAnalyzerAndTokenizer() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customAnalyzers(IndexCreationCustomElement.from("my_custom_analyzer", "{" +
                "    \"type\": \"custom\"," +
                "    \"tokenizer\": \"custom_tokenizer\"," +
                "    \"filter\": [" +
                "        \"lowercase\"" +
                "    ]" +
                "}"))
            .customTokenizers(IndexCreationCustomElement.from("custom_tokenizer", "{" +
                "    \"type\": \"pattern\"," +
                "    \"pattern\": \"[ .,!?]\"," +
                "    \"flags\": \"CASE_INSENSITIVE|COMMENTS\"," +
                "    \"group\": 0" +
                "}"))
            .createIndexAndAliases(client);
    }

    @Test
    void customIndexSettingShouldNotThrowWhenValidSetting() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client, Optional.of(getValidIndexSetting()), Optional.empty());
    }

    @Test
    void customIndexSettingShouldThrowWhenInvalidSetting() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .createIndexAndAliases(client, Optional.of(getInvalidIndexSetting()), Optional.empty()))
            .isInstanceOf(ElasticsearchException.class);
    }

}