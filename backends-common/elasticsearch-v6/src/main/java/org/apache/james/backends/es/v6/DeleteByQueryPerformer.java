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

package org.apache.james.backends.es.v6;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;

import com.google.common.annotations.VisibleForTesting;

public class DeleteByQueryPerformer {
    public static final TimeValue TIMEOUT = new TimeValue(60000);

    private final RestHighLevelClient client;
    private final ExecutorService executor;
    private final int batchSize;
    private final WriteAliasName aliasName;
    private final TypeName typeName;

    @VisibleForTesting
    public DeleteByQueryPerformer(RestHighLevelClient client, ExecutorService executor, int batchSize, WriteAliasName aliasName, TypeName typeName) {
        this.client = client;
        this.executor = executor;
        this.batchSize = batchSize;
        this.aliasName = aliasName;
        this.typeName = typeName;
    }

    public Future<Void> perform(QueryBuilder queryBuilder) {
        return executor.submit(() -> doDeleteByQuery(queryBuilder));
    }

    protected Void doDeleteByQuery(QueryBuilder queryBuilder) throws IOException {
        DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName.getValue())
            .setDocTypes(typeName.getValue())
            .setScroll(TIMEOUT)
            .setQuery(queryBuilder)
            .setBatchSize(batchSize);

        client.deleteByQuery(request, RequestOptions.DEFAULT);
        return null;
    }
}
