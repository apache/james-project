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
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class ElasticSearchIndexer {
    private static final int DEBUG_MAX_LENGTH_CONTENT = 1000;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final TimeValue TIMEOUT = new TimeValue(60000);

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexer.class);

    private final RestHighLevelClient client;
    private final AliasName aliasName;
    private final int batchSize;

    public ElasticSearchIndexer(RestHighLevelClient client,
                                WriteAliasName aliasName) {
        this(client, aliasName, DEFAULT_BATCH_SIZE);
    }

    @VisibleForTesting
    public ElasticSearchIndexer(RestHighLevelClient client,
                                WriteAliasName aliasName,
                                int batchSize) {
        this.client = client;
        this.aliasName = aliasName;
        this.batchSize = batchSize;
    }

    public IndexResponse index(String id, String content) throws IOException {
        checkArgument(content);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Indexing {}: {}", id, StringUtils.left(content, DEBUG_MAX_LENGTH_CONTENT));
        }
        return client.index(
            new IndexRequest(aliasName.getValue())
                .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                .id(id)
                .source(content, XContentType.JSON),
            RequestOptions.DEFAULT);
    }

    public Optional<BulkResponse> update(List<UpdatedRepresentation> updatedDocumentParts) throws IOException {
        try {
            Preconditions.checkNotNull(updatedDocumentParts);
            BulkRequest request = new BulkRequest();
            updatedDocumentParts.forEach(updatedDocumentPart -> request.add(
                new UpdateRequest(aliasName.getValue(),
                    NodeMappingFactory.DEFAULT_MAPPING_NAME,
                    updatedDocumentPart.getId())
                .doc(updatedDocumentPart.getUpdatedDocumentPart(), XContentType.JSON)));
            return Optional.of(client.bulk(request, RequestOptions.DEFAULT));
        } catch (ValidationException e) {
            LOGGER.warn("Error while updating index", e);
            return Optional.empty();
        }
    }

    public Optional<BulkResponse> delete(List<String> ids) throws IOException {
        try {
            BulkRequest request = new BulkRequest();
            ids.forEach(id -> request.add(
                new DeleteRequest(aliasName.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id)));
            return Optional.of(client.bulk(request, RequestOptions.DEFAULT));
        } catch (ValidationException e) {
            LOGGER.warn("Error while deleting index", e);
            return Optional.empty();
        }
    }

    public void deleteAllMatchingQuery(QueryBuilder queryBuilder) {
        DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName.getValue())
            .setDocTypes(NodeMappingFactory.DEFAULT_MAPPING_NAME)
            .setScroll(TIMEOUT)
            .setQuery(queryBuilder)
            .setBatchSize(batchSize);

        client.deleteByQueryAsync(request, RequestOptions.DEFAULT, new ListenerToFuture<>());
    }

    private void checkArgument(String content) {
        Preconditions.checkArgument(content != null, "content should be provided");
    }
}
