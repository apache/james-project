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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class ElasticSearchIndexer {
    private static final int DEBUG_MAX_LENGTH_CONTENT = 1000;
    private static final int DEFAULT_BATCH_SIZE = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexer.class);

    private final ReactorElasticSearchClient client;
    private final AliasName aliasName;
    private final DeleteByQueryPerformer deleteByQueryPerformer;

    public ElasticSearchIndexer(ReactorElasticSearchClient client,
                                WriteAliasName aliasName) {
        this(client, aliasName, DEFAULT_BATCH_SIZE);
    }

    @VisibleForTesting
    public ElasticSearchIndexer(ReactorElasticSearchClient client,
                                WriteAliasName aliasName,
                                int batchSize) {
        this.client = client;
        this.deleteByQueryPerformer = new DeleteByQueryPerformer(client, batchSize, aliasName);
        this.aliasName = aliasName;
    }

    public Mono<IndexResponse> index(DocumentId id, String content, RoutingKey routingKey) {
        checkArgument(content);
        logContent(id, content);
        return client.index(new IndexRequest(aliasName.getValue())
                .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                .id(id.asString())
                .source(content, XContentType.JSON)
                .routing(routingKey.asString()),
            RequestOptions.DEFAULT);
    }

    private void logContent(DocumentId id, String content) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Indexing {}: {}", id.asString(), StringUtils.left(content, DEBUG_MAX_LENGTH_CONTENT));
        }
    }

    public Mono<BulkResponse> update(List<UpdatedRepresentation> updatedDocumentParts, RoutingKey routingKey) {
        Preconditions.checkNotNull(updatedDocumentParts);
        Preconditions.checkNotNull(routingKey);
        BulkRequest request = new BulkRequest();
        updatedDocumentParts.forEach(updatedDocumentPart -> request.add(
            new UpdateRequest(aliasName.getValue(),
                NodeMappingFactory.DEFAULT_MAPPING_NAME,
                updatedDocumentPart.getId().asString())
                .doc(updatedDocumentPart.getUpdatedDocumentPart(), XContentType.JSON)
                .routing(routingKey.asString())));

        return client.bulk(request, RequestOptions.DEFAULT)
            .onErrorResume(ValidationException.class, exception -> {
                LOGGER.warn("Error while updating index", exception);
                return Mono.empty();
            });
    }

    public Mono<BulkResponse> delete(List<DocumentId> ids, RoutingKey routingKey) {
        BulkRequest request = new BulkRequest();
        ids.forEach(id -> request.add(
            new DeleteRequest(aliasName.getValue())
                .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                .id(id.asString())
                .routing(routingKey.asString())));

        return client.bulk(request, RequestOptions.DEFAULT)
            .onErrorResume(ValidationException.class, exception -> {
                LOGGER.warn("Error while deleting index", exception);
                return Mono.empty();
            });
    }

    public Mono<Void> deleteAllMatchingQuery(QueryBuilder queryBuilder, RoutingKey routingKey) {
        // TODO use https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete-by-query.html
        return deleteByQueryPerformer.perform(queryBuilder, routingKey);
    }

    private void checkArgument(String content) {
        Preconditions.checkArgument(content != null, "content should be provided");
    }

    public Mono<GetResponse> get(DocumentId id, RoutingKey routingKey) {
        return Mono.fromRunnable(() -> {
                Preconditions.checkNotNull(id);
                Preconditions.checkNotNull(routingKey);
            })
            .then(client.get(new GetRequest(aliasName.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(id.asString())
                    .routing(routingKey.asString()),
                RequestOptions.DEFAULT));
    }
}
