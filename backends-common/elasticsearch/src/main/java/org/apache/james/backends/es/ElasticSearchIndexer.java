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

import java.util.List;

import javax.inject.Inject;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class ElasticSearchIndexer {

    public static class UpdatedRepresentation {
        private final String id;
        private final String updatedDocumentPart;

        public UpdatedRepresentation(String id, String updatedDocumentPart) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "Updated id must be specified " + id);
            Preconditions.checkArgument(!Strings.isNullOrEmpty(updatedDocumentPart), "Updated document must be specified");
            this.id = id;
            this.updatedDocumentPart = updatedDocumentPart;
        }

        public String getId() {
            return id;
        }

        public String getUpdatedDocumentPart() {
            return updatedDocumentPart;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexer.class);
    
    private final Client client;
    private final DeleteByQueryPerformer deleteByQueryPerformer;
    private final String indexName;
    private final String typeName;

    @Inject
    public ElasticSearchIndexer(Client client, DeleteByQueryPerformer deleteByQueryPerformer, String indexName, String typeName) {
        this.client = client;
        this.deleteByQueryPerformer = deleteByQueryPerformer;
        this.indexName = indexName;
        this.typeName = typeName;
    }
    
    public IndexResponse indexMessage(String id, String content) {
        checkArgument(content);
        LOGGER.debug(String.format("Indexing %s: %s", id, content));
        return client.prepareIndex(indexName, typeName, id)
            .setSource(content)
            .get();
    }

    public BulkResponse updateMessages(List<UpdatedRepresentation> updatedDocumentParts) {
        Preconditions.checkNotNull(updatedDocumentParts);
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        updatedDocumentParts.forEach(updatedDocumentPart -> bulkRequestBuilder.add(client.prepareUpdate(indexName, typeName, updatedDocumentPart.getId())
            .setDoc(updatedDocumentPart.getUpdatedDocumentPart())));
        return bulkRequestBuilder.get();
    }

    public BulkResponse deleteMessages(List<String> ids) {
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        ids.forEach(id -> bulkRequestBuilder.add(client.prepareDelete(indexName, typeName, id)));
        return bulkRequestBuilder.get();
    }
    
    public void deleteAllMatchingQuery(QueryBuilder queryBuilder) {
        deleteByQueryPerformer.perform(queryBuilder);
    }

    private void checkArgument(String content) {
        Preconditions.checkArgument(content != null, "content should be provided");
    }
}
