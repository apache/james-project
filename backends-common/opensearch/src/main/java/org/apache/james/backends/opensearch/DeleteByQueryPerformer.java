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

import java.io.IOException;

import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;

import reactor.core.publisher.Mono;

public class DeleteByQueryPerformer {

    private final ReactorElasticSearchClient client;
    private final WriteAliasName aliasName;

    DeleteByQueryPerformer(ReactorElasticSearchClient client, WriteAliasName aliasName) {
        this.client = client;
        this.aliasName = aliasName;
    }

    public Mono<Void> perform(Query query, RoutingKey routingKey) {
        DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest.Builder()
            .index(aliasName.getValue())
            .query(query)
            .routing(routingKey.asString())
            .build();

        try {
            return client.deleteByQuery(deleteRequest)
                .then();
        } catch (IOException e) {
            return Mono.error(e);
        }
    }
}
