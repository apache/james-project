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

package org.apache.james.quota.search.elasticsearch;

import java.io.IOException;

import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.ReactorElasticSearchClient;

public class QuotaSearchIndexCreationUtil {

    public static ReactorElasticSearchClient prepareClient(ReactorElasticSearchClient client,
                                       AliasName readAlias,
                                       AliasName writeAlias,
                                       IndexName indexName,
                                       ElasticSearchConfiguration configuration) throws IOException {

        return NodeMappingFactory.applyMapping(
            new IndexCreationFactory(configuration)
                .useIndex(indexName)
                .addAlias(readAlias)
                .addAlias(writeAlias)
                .createIndexAndAliases(client),
            indexName,
            QuotaRatioMappingFactory.getMappingContent());
    }

    public static ReactorElasticSearchClient prepareDefaultClient(ReactorElasticSearchClient client, ElasticSearchConfiguration configuration) throws IOException {
        return prepareClient(client,
            QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS,
            QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS,
            QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX,
            configuration);
    }
}
