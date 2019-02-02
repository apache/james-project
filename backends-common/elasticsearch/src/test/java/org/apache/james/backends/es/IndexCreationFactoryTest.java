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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.es.utils.TestingClientProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class IndexCreationFactoryTest {
    public static final IndexName INDEX_NAME = new IndexName("index");
    public static final ReadAliasName ALIAS_NAME = new ReadAliasName("alias");

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    private ClientProvider clientProvider;

    @Before
    public void setUp() {
        clientProvider = new TestingClientProvider(embeddedElasticSearch.getNode());
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(clientProvider.get());
    }

    @Test
    public void createIndexAndAliasShouldNotThrowWhenCalledSeveralTime() {
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(clientProvider.get());
    }

    @Test
    public void useIndexShouldThrowWhenNull() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .useIndex(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void addAliasShouldThrowWhenNull() {
        assertThatThrownBy(() ->
            new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
                .addAlias(null))
            .isInstanceOf(NullPointerException.class);
    }
}