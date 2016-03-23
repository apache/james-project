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

package org.apache.james.mailbox.elasticsearch;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.mailbox.elasticsearch.search.ScrollIterable;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;

public class DeleteByQueryPerformer {
    public static final int BATCH_SIZE = 100;
    public static final TimeValue TIMEOUT = new TimeValue(60000);

    private final ClientProvider clientProvider;
    private final ExecutorService executor;

    @Inject
    public DeleteByQueryPerformer(ClientProvider clientProvider, @Named("AsyncExecutor") ExecutorService executor) {
        this.clientProvider = clientProvider;
        this.executor = executor;
    }

    public void perform(QueryBuilder queryBuilder) {
        executor.execute(() -> doDeleteByQuery(queryBuilder));
    }

    private Void doDeleteByQuery(QueryBuilder queryBuilder) {
        try (Client client = clientProvider.get()) {
            ScrollIterable scrollIterable = new ScrollIterable(client,
                client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setScroll(TIMEOUT)
                    .setNoFields()
                    .setQuery(queryBuilder)
                    .setSize(BATCH_SIZE));
            for (SearchResponse searchResponse : scrollIterable) {
                deleteRetrievedIds(client, searchResponse);
            }
            return null;
        }
    }

    private ListenableActionFuture<BulkResponse> deleteRetrievedIds(Client client, SearchResponse searchResponse) {
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        for (SearchHit hit : searchResponse.getHits()) {
            bulkRequestBuilder.add(client.prepareDelete()
                .setIndex(ElasticSearchIndexer.MAILBOX_INDEX)
                .setType(ElasticSearchIndexer.MESSAGE_TYPE)
                .setId(hit.getId()));
        }
        return bulkRequestBuilder.execute();
    }

}
