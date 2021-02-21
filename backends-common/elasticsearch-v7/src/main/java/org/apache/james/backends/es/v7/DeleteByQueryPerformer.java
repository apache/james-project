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

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class DeleteByQueryPerformer {

    private final ReactorElasticSearchClient client;
    private final WriteAliasName aliasName;

    @VisibleForTesting
    DeleteByQueryPerformer(ReactorElasticSearchClient client, WriteAliasName aliasName) {
        this.client = client;
        this.aliasName = aliasName;
    }

    public Mono<Void> perform(QueryBuilder queryBuilder, RoutingKey routingKey) {
        DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(aliasName.getValue());
        deleteRequest.setQuery(queryBuilder);
        deleteRequest.setRouting(routingKey.asString());

        return client.deleteByQuery(deleteRequest, RequestOptions.DEFAULT)
            .then();
    }
}
