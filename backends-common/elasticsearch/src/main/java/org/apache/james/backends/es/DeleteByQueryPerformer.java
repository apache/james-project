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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.james.backends.es.search.ScrollIterable;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;

import com.google.common.annotations.VisibleForTesting;

public class DeleteByQueryPerformer {
    public static final TimeValue TIMEOUT = new TimeValue(60000);

    private final Client client;
    private final ExecutorService executor;
    private final int batchSize;
    private final WriteAliasName aliasName;
    private final TypeName typeName;

    @VisibleForTesting
    public DeleteByQueryPerformer(Client client, ExecutorService executor, int batchSize, WriteAliasName aliasName, TypeName typeName) {
        this.client = client;
        this.executor = executor;
        this.batchSize = batchSize;
        this.aliasName = aliasName;
        this.typeName = typeName;
    }

    public Future<Void> perform(QueryBuilder queryBuilder) {
        return executor.submit(() -> doDeleteByQuery(queryBuilder));
    }

    protected Void doDeleteByQuery(QueryBuilder queryBuilder) {
        new ScrollIterable(client,
            client.prepareSearch(aliasName.getValue())
                .setTypes(typeName.getValue())
                .setScroll(TIMEOUT)
                .setNoFields()
                .setQuery(queryBuilder)
                .setSize(batchSize))
            .stream()
            .map(searchResponse -> deleteRetrievedIds(client, searchResponse))
            .forEach(ListenableActionFuture::actionGet);
        return null;
    }

    private ListenableActionFuture<BulkResponse> deleteRetrievedIds(Client client, SearchResponse searchResponse) {
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        for (SearchHit hit : searchResponse.getHits()) {
            bulkRequestBuilder.add(client.prepareDelete()
                .setIndex(aliasName.getValue())
                .setType(typeName.getValue())
                .setId(hit.getId()));
        }
        return bulkRequestBuilder.execute();
    }

}
