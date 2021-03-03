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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.Optional;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NodeMappingFactoryTest {
    private static final String MESSAGE = "message";
    private static final IndexName INDEX_NAME = new IndexName("index");
    private static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

    @RegisterExtension
    public DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();
    private ReactorElasticSearchClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = elasticSearch.getDockerElasticSearch().clientProvider().get();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliasesWithMapping(client, Optional.of(getMappingsSources()));
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void applyMappingShouldNotThrowWhenCalledSeveralTime() throws Exception {
        NodeMappingFactory.applyMapping(client,
            INDEX_NAME,
            getMappingsSources());
    }

    @Test
    void applyMappingShouldNotThrowWhenIncrementalChanges() throws Exception {
        NodeMappingFactory.applyMapping(client,
            INDEX_NAME,
            getMappingsSources());

        elasticSearch.awaitForElasticSearch();

        assertThatCode(() -> NodeMappingFactory.applyMapping(client,
            INDEX_NAME,
            getOtherMappingsSources()))
        .doesNotThrowAnyException();
    }

    private XContentBuilder getMappingsSources() throws Exception {
        return jsonBuilder()
            .startObject()
                .startObject(NodeMappingFactory.PROPERTIES)
                    .startObject(MESSAGE)
                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.TEXT)
                    .endObject()
                .endObject()
            .endObject();
    }

    private XContentBuilder getOtherMappingsSources() throws Exception {
        return jsonBuilder()
            .startObject()
                .startObject(NodeMappingFactory.PROPERTIES)
                    .startObject(MESSAGE)
                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.TEXT)
                        .field(NodeMappingFactory.INDEX, false)
                    .endObject()
                .endObject()
            .endObject();
    }
}