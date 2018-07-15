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

package org.apache.james.backends.es;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import org.apache.james.backends.es.utils.TestingClientProvider;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class NodeMappingFactoryTest {
    public static final String MESSAGE = "message";
    public static final IndexName INDEX_NAME = new IndexName("index");
    public static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");
    public static final TypeName TYPE_NAME = new TypeName("type");

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    private ClientProvider clientProvider;

    @Before
    public void setUp() throws Exception {
        clientProvider = new TestingClientProvider(embeddedElasticSearch.getNode());
        new IndexCreationFactory()
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(clientProvider.get());
        NodeMappingFactory.applyMapping(clientProvider.get(),
            INDEX_NAME,
            TYPE_NAME,
            getMappingsSources());
    }

    @Test
    public void applyMappingShouldNotThrowWhenCalledSeveralTime() throws Exception {
        NodeMappingFactory.applyMapping(clientProvider.get(),
            INDEX_NAME,
            TYPE_NAME,
            getMappingsSources());
    }

    @Test
    public void applyMappingShouldNotThrowWhenIndexerChanges() throws Exception {
        NodeMappingFactory.applyMapping(clientProvider.get(),
            INDEX_NAME,
            TYPE_NAME,
            getMappingsSources());

        embeddedElasticSearch.awaitForElasticSearch();

        NodeMappingFactory.applyMapping(clientProvider.get(),
            INDEX_NAME,
            TYPE_NAME,
            getOtherMappingsSources());
    }

    private XContentBuilder getMappingsSources() throws Exception {
        return jsonBuilder()
            .startObject()
                .startObject(TYPE_NAME.getValue())
                    .startObject(NodeMappingFactory.PROPERTIES)
                        .startObject(MESSAGE)
                            .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }

    private XContentBuilder getOtherMappingsSources() throws Exception {
        return jsonBuilder()
            .startObject()
                .startObject(TYPE_NAME.getValue())
                    .startObject(NodeMappingFactory.PROPERTIES)
                        .startObject(MESSAGE)
                            .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                            .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
    }
}