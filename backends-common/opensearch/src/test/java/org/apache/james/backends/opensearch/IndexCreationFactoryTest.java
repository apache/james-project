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

import static org.apache.james.backends.opensearch.IndexCreationFactory.ANALYZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.TOKENIZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.TYPE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.backends.opensearch.IndexCreationFactory.IndexCreationCustomElement;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class IndexCreationFactoryTest {
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

    public static XContentBuilder getValidIndexSetting() throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject("settings")
                    .startObject("index")
                        .field("max_ngram_diff", 10)
                    .endObject()
                    .startObject("analysis")
                        .startObject(ANALYZER)
                            .startObject("email_ngram_filter_analyzer")
                                .field(TOKENIZER, "uax_url_email")
                                .startArray("filter")
                                    .value("ngram_filter")
                                .endArray()
                            .endObject()
                        .endObject()
                        .startObject("filter")
                            .startObject("ngram_filter")
                                .field(TYPE, "ngram")
                                .field("min_gram", 3)
                                .field("max_gram", 13)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }

    public static XContentBuilder getInvalidIndexSetting() throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject("settings")
                    .startObject("analysis")
                        .startObject(ANALYZER)
                            .startObject("email_ngram_filter_analyzer")
                                .field(TOKENIZER, "uax_url_email")
                                .startArray("filter")
                                    .value("ngram_filter")
                                .endArray()
                            .endObject()
                        .endObject()
                        .startObject("filter")
                            .startObject("ngram_filter")
                                .field(TYPE, "ngram")
                                .field("min_gram", 3)
                                .field("max_gram", 13)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
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
            .customAnalyzers(IndexCreationCustomElement.from("{" +
                "    \"my_custom_analyzer\": {" +
                "        \"type\": \"custom\"," +
                "        \"tokenizer\": \"standard\"," +
                "        \"char_filter\": [" +
                "            \"html_strip\"" +
                "        ]," +
                "        \"filter\": [" +
                "            \"lowercase\"," +
                "            \"asciifolding\"" +
                "        ]" +
                "    }" +
                "}"))
            .createIndexAndAliases(client);
    }

    @Test
    void customAnalyzerShouldThrowWhenInValidAnalyzer() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customAnalyzers(IndexCreationCustomElement.from("{" +
                    "    \"my_custom_analyzer\": {" +
                    "        \"type\": \"invalid\"," +
                    "        \"tokenizer\": \"not_Found_tokenizer\"" +
                    "    }" +
                    "}"))
                .createIndexAndAliases(client))
            .isInstanceOf(OpenSearchStatusException.class);
    }

    @Test
    void customTokenizersShouldNotThrowWhenValidTokenizers() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customTokenizers(IndexCreationCustomElement.from("{" +
                "        \"custom_tokenizer\": { " +
                "          \"type\": \"pattern\"," +
                "          \"pattern\": \"[ .,!?]\"" +
                "        }" +
                "      }"))
            .createIndexAndAliases(client);
    }

    @Test
    void customTokenizersShouldThrowWhenInValidTokenizers() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customTokenizers(IndexCreationCustomElement.from("{" +
                    "        \"custom_tokenizer\": { " +
                    "          \"type\": \"invalidType\"," +
                    "          \"pattern\": \"[ .,!?]\"" +
                    "        }" +
                    "      }"))
                .createIndexAndAliases(client))
            .isInstanceOf(OpenSearchStatusException.class);
    }

    @Test
    void createIndexShouldNotThrowWhenProvidedValidCustomAnalyzerAndTokenizer() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customAnalyzers(IndexCreationCustomElement.from("{" +
                "        \"my_custom_analyzer\": { " +
                "          \"tokenizer\": \"custom_tokenizer\"," +
                "          \"filter\": [" +
                "            \"lowercase\"" +
                "          ]" +
                "        }" +
                "      }"))
            .customTokenizers(IndexCreationCustomElement.from("{" +
                "        \"custom_tokenizer\": { " +
                "          \"type\": \"pattern\"," +
                "          \"pattern\": \"[ .,!?]\"" +
                "        }" +
                "      }"))
            .createIndexAndAliases(client);
    }

    @Test
    void customIndexSettingShouldNotThrowWhenValidSetting() throws IOException {
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
            .isInstanceOf(OpenSearchStatusException.class);
    }

}