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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.backends.opensearch.IndexCreationFactory.IndexCreationCustomAnalyzer;
import org.apache.james.backends.opensearch.IndexCreationFactory.IndexCreationCustomTokenizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch._types.analysis.Analyzer;
import org.opensearch.client.opensearch._types.analysis.CustomAnalyzer;
import org.opensearch.client.opensearch._types.analysis.NGramTokenFilter;
import org.opensearch.client.opensearch._types.analysis.PatternTokenizer;
import org.opensearch.client.opensearch._types.analysis.TokenFilter;
import org.opensearch.client.opensearch._types.analysis.TokenFilterDefinition;
import org.opensearch.client.opensearch._types.analysis.Tokenizer;
import org.opensearch.client.opensearch._types.analysis.TokenizerDefinition;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettingsAnalysis;

@ExtendWith(DockerOpenSearchExtension.class)
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
                    .custom(generateAnalyzer())
                    .build())
                .filter("ngram_filter", new TokenFilter.Builder()
                    .definition(new TokenFilterDefinition.Builder()
                        .ngram(generateFilter())
                        .build())
                    .build())
                .build())
            .build();
    }

    public static IndexSettings getInvalidIndexSetting() {
        return new IndexSettings.Builder()
            .analysis(new IndexSettingsAnalysis.Builder()
                .analyzer("email_ngram_filter_analyzer", new Analyzer.Builder()
                    .custom(generateAnalyzer())
                    .build())
                .filter("ngram_filter", new TokenFilter.Builder()
                    .definition(new TokenFilterDefinition.Builder()
                        .ngram(generateFilter())
                        .build())
                    .build())
                .build())
            .build();
    }

    private static CustomAnalyzer generateAnalyzer() {
        return new CustomAnalyzer.Builder()
            .tokenizer("uax_url_email")
            .filter("ngram_filter")
            .build();
    }

    private static NGramTokenFilter generateFilter() {
        return new NGramTokenFilter.Builder()
            .minGram(3)
            .maxGram(13)
            .build();
    }

    private ReactorOpenSearchClient client;

    @BeforeEach
    void setUp(DockerOpenSearch dockerOpenSearch) {
        client = dockerOpenSearch.clientProvider().get();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void createIndexAndAliasShouldNotThrowWhenCalledSeveralTime() {
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);

        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client);
    }

    @Test
    void useIndexShouldThrowWhenNull() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void customAnalyzerShouldNotThrowWhenValidAnalyzer() {
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customAnalyzers(new IndexCreationCustomAnalyzer("my_custom_analyzer",
                new Analyzer.Builder()
                    .custom(new CustomAnalyzer.Builder()
                        .tokenizer("standard")
                        .filter("lowercase", "asciifolding")
                        .charFilter("html_strip")
                        .build())
                    .build()))
            .createIndexAndAliases(client);
    }

    @Test
    void customAnalyzerShouldThrowWhenInValidAnalyzer() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customAnalyzers(new IndexCreationCustomAnalyzer("my_custom_analyzer",
                    new Analyzer.Builder()
                        .custom(new CustomAnalyzer.Builder()
                            .tokenizer("not_Found_tokenizer")
                            .build())
                        .build()))
                .createIndexAndAliases(client))
            .isInstanceOf(Exception.class);
    }

    @Test
    void customTokenizersShouldNotThrowWhenValidTokenizers() {
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customTokenizers(new IndexCreationCustomTokenizer("custom_tokenizer",
                new Tokenizer.Builder()
                    .definition(new TokenizerDefinition.Builder()
                        .pattern(new PatternTokenizer.Builder()
                            .pattern("[ .,!?]")
                            .flags("CASE_INSENSITIVE|COMMENTS")
                            .group(0)
                            .build())
                        .build())
                    .build()))
            .createIndexAndAliases(client);
    }

    @Test
    void customTokenizersShouldThrowWhenInValidTokenizers() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .customTokenizers(new IndexCreationCustomTokenizer("custom_tokenizer",
                    new Tokenizer.Builder()
                        .definition(new TokenizerDefinition.Builder()
                            .pattern(new PatternTokenizer.Builder()
                                .pattern("[ .,!?]")
                                .build())
                            .build())
                        .build()))
                .createIndexAndAliases(client))
            .isInstanceOf(Exception.class);
    }

    @Test
    void createIndexShouldNotThrowWhenProvidedValidCustomAnalyzerAndTokenizer() {
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .customAnalyzers(new IndexCreationCustomAnalyzer("my_custom_analyzer",
                new Analyzer.Builder()
                    .custom(new CustomAnalyzer.Builder()
                        .tokenizer("custom_tokenizer")
                        .filter("lowercase")
                        .build())
                    .build()))
            .customTokenizers(new IndexCreationCustomTokenizer("custom_tokenizer",
                new Tokenizer.Builder()
                    .definition(new TokenizerDefinition.Builder()
                        .pattern(new PatternTokenizer.Builder()
                            .pattern("[ .,!?]")
                            .flags("CASE_INSENSITIVE|COMMENTS")
                            .group(0)
                            .build())
                        .build())
                    .build()))
            .createIndexAndAliases(client);
    }

    @Test
    void customIndexSettingShouldNotThrowWhenValidSetting() {
        new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client, Optional.of(getValidIndexSetting()), Optional.empty());
    }

    @Test
    void customIndexSettingShouldThrowWhenInvalidSetting() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(INDEX_NAME)
                .addAlias(ALIAS_NAME)
                .createIndexAndAliases(client, Optional.of(getInvalidIndexSetting()), Optional.empty()))
            .isInstanceOf(Exception.class);
    }

}