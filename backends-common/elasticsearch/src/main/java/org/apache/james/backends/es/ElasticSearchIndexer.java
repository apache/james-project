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
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class ElasticSearchIndexer {
    private static int DEBUG_MAX_LENGTH_CONTENT = 1000;

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

        @Override
        public final boolean equals(Object o) {
            if (o instanceof UpdatedRepresentation) {
                UpdatedRepresentation other = (UpdatedRepresentation) o;
                return Objects.equals(id, other.id)
                    && Objects.equals(updatedDocumentPart, other.updatedDocumentPart);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(id, updatedDocumentPart);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("updatedDocumentPart", updatedDocumentPart)
                .toString();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchIndexer.class);
    
    private final Client client;
    private final DeleteByQueryPerformer deleteByQueryPerformer;
    private final AliasName aliasName;
    private final TypeName typeName;

    @Inject
    public ElasticSearchIndexer(Client client, DeleteByQueryPerformer deleteByQueryPerformer, AliasName aliasName, TypeName typeName) {
        this.client = client;
        this.deleteByQueryPerformer = deleteByQueryPerformer;
        this.aliasName = aliasName;
        this.typeName = typeName;
    }
    
    public IndexResponse indexMessage(String id, String content) {
        checkArgument(content);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Indexing {}: {}", id, StringUtils.left(content, DEBUG_MAX_LENGTH_CONTENT));
        }
        return client.prepareIndex(aliasName.getValue(), typeName.getValue(), id)
            .setSource(content)
            .get();
    }

    public Optional<BulkResponse> updateMessages(List<UpdatedRepresentation> updatedDocumentParts) {
        try {
            Preconditions.checkNotNull(updatedDocumentParts);
            BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
            updatedDocumentParts.forEach(updatedDocumentPart -> bulkRequestBuilder.add(
                client.prepareUpdate(
                    aliasName.getValue(),
                    typeName.getValue(),
                    updatedDocumentPart.getId())
                .setDoc(updatedDocumentPart.getUpdatedDocumentPart())));
            return Optional.of(bulkRequestBuilder.get());
        } catch (ValidationException e) {
            LOGGER.warn("Error while updating index", e);
            return Optional.empty();
        }
    }

    public Optional<BulkResponse> deleteMessages(List<String> ids) {
        try {
            BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
            ids.forEach(id -> bulkRequestBuilder.add(
                client.prepareDelete(
                    aliasName.getValue(),
                    typeName.getValue(),
                    id)));
            return Optional.of(bulkRequestBuilder.get());
        } catch (ValidationException e) {
            LOGGER.warn("Error while deleting index", e);
            return Optional.empty();
        }
    }
    
    public void deleteAllMatchingQuery(QueryBuilder queryBuilder) {
        deleteByQueryPerformer.perform(queryBuilder);
    }

    private void checkArgument(String content) {
        Preconditions.checkArgument(content != null, "content should be provided");
    }
}
