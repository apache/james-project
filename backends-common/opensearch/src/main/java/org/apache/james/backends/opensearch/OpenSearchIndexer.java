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
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class OpenSearchIndexer {
    private static final int DEBUG_MAX_LENGTH_CONTENT = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchIndexer.class);

    private final ReactorOpenSearchClient client;
    private final AliasName aliasName;
    private final DeleteByQueryPerformer deleteByQueryPerformer;

    public OpenSearchIndexer(ReactorOpenSearchClient client,
                             WriteAliasName aliasName) {
        this.client = client;
        this.deleteByQueryPerformer = new DeleteByQueryPerformer(client, aliasName);
        this.aliasName = aliasName;
    }

    public Mono<IndexResponse> index(DocumentId id, String content, RoutingKey routingKey) {
        checkArgument(content);
        logContent(id, content);

        try {
            return client.index(new IndexRequest.Builder<>()
                .index(aliasName.getValue())
                .id(id.asString())
                .document(new RawValue(content))
                .routing(routingKey.asString())
                .build());
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private void logContent(DocumentId id, String content) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Indexing {}: {}", id.asString(), StringUtils.left(content, DEBUG_MAX_LENGTH_CONTENT));
        }
    }

    public Mono<BulkResponse> update(List<UpdatedRepresentation> updatedDocumentParts, RoutingKey routingKey) {
        Preconditions.checkNotNull(updatedDocumentParts);
        Preconditions.checkNotNull(routingKey);

        if (updatedDocumentParts.isEmpty()) {
            return Mono.empty();
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        updatedDocumentParts.forEach(updatedDocumentPart -> bulkBuilder.operations(
            op -> op.update(idx -> idx
                .index(aliasName.getValue())
                .id(updatedDocumentPart.getId().asString())
                .document(new RawValue(updatedDocumentPart.getUpdatedDocumentPart()))
                .routing(routingKey.asString())
            )));

        try {
            return client.bulk(bulkBuilder.build());
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    public Mono<BulkResponse> delete(List<DocumentId> ids, RoutingKey routingKey) {
        if (ids.isEmpty()) {
            return Mono.empty();
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        ids.forEach(id -> bulkBuilder.operations(
            op -> op.delete(idx -> idx
                .index(aliasName.getValue())
                .id(id.asString())
                .routing(routingKey.asString())
            )));

        try {
            return client.bulk(bulkBuilder.build());
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    public Mono<Void> deleteAllMatchingQuery(Query query, RoutingKey routingKey) {
        return deleteByQueryPerformer.perform(query, routingKey);
    }

    private void checkArgument(String content) {
        Preconditions.checkArgument(content != null, "content should be provided");
    }

    public Mono<GetResponse<ObjectNode>> get(DocumentId id, RoutingKey routingKey) {
        try {
            return Mono.fromRunnable(() -> {
                    Preconditions.checkNotNull(id);
                    Preconditions.checkNotNull(routingKey);
                })
                .then(client.get(
                    new GetRequest.Builder()
                        .index(aliasName.getValue())
                        .id(id.asString())
                        .routing(routingKey.asString())
                        .build()));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }
}
