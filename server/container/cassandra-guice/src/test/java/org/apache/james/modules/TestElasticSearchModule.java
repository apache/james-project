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

package org.apache.james.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.elasticsearch.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.utils.TestingClientProvider;

import javax.inject.Singleton;

public class TestElasticSearchModule extends AbstractModule{

    private final EmbeddedElasticSearch embeddedElasticSearch;

    public TestElasticSearchModule(EmbeddedElasticSearch embeddedElasticSearch) {
        this.embeddedElasticSearch = embeddedElasticSearch;
    }

    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    protected ClientProvider provideClientProvider() {
        return NodeMappingFactory.applyMapping(
            IndexCreationFactory.createIndex(new TestingClientProvider(embeddedElasticSearch.getNode()))
        );
    }
}
