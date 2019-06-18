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

package org.apache.james.mailbox.elasticsearch.search;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.search.ScrolledSearch;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.query.SortConverter;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class ElasticSearchSearcher {
    public static final int DEFAULT_SEARCH_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearcher.class);
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);
    private static final ImmutableList<String> STORED_FIELDS = ImmutableList.of(JsonMessageConstants.MAILBOX_ID,
        JsonMessageConstants.UID, JsonMessageConstants.MESSAGE_ID);

    private final RestHighLevelClient client;
    private final QueryConverter queryConverter;
    private final int size;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;
    private final AliasName aliasName;

    public ElasticSearchSearcher(RestHighLevelClient client, QueryConverter queryConverter, int size,
                                 MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory,
                                 ReadAliasName aliasName) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
        this.aliasName = aliasName;
    }

    public Stream<MessageSearchIndex.SearchResult> search(Collection<MailboxId> mailboxIds, SearchQuery query,
                                                          Optional<Integer> limit) {
        SearchRequest searchRequest = prepareSearch(mailboxIds, query, limit);
        Stream<MessageSearchIndex.SearchResult> pairStream = new ScrolledSearch(client, searchRequest)
            .searchHits()
            .flatMap(this::extractContentFromHit);

        return limit.map(pairStream::limit)
            .orElse(pairStream);
    }

    private SearchRequest prepareSearch(Collection<MailboxId> users, SearchQuery query, Optional<Integer> limit) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(queryConverter.from(users, query))
            .size(computeRequiredSize(limit))
            .storedFields(STORED_FIELDS);

        query.getSorts()
            .stream()
            .map(SortConverter::convertSort)
            .forEach(searchSourceBuilder::sort);

        return new SearchRequest(aliasName.getValue())
            .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
            .scroll(TIMEOUT)
            .source(searchSourceBuilder);
    }

    private int computeRequiredSize(Optional<Integer> limit) {
        return limit.map(value -> Math.min(value.intValue(), size))
            .orElse(size);
    }

    private Stream<MessageSearchIndex.SearchResult> extractContentFromHit(SearchHit hit) {
        DocumentField mailboxId = hit.field(JsonMessageConstants.MAILBOX_ID);
        DocumentField uid = hit.field(JsonMessageConstants.UID);
        Optional<DocumentField> id = retrieveMessageIdField(hit);
        if (mailboxId != null && uid != null) {
            Number uidAsNumber = uid.getValue();
            return Stream.of(
                new MessageSearchIndex.SearchResult(
                    id.map(field -> messageIdFactory.fromString(field.getValue())),
                    mailboxIdFactory.fromString(mailboxId.getValue()),
                    MessageUid.of(uidAsNumber.longValue())));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result {}", hit.getId());
            return Stream.empty();
        }
    }

    private Optional<DocumentField> retrieveMessageIdField(SearchHit hit) {
        if (hit.getFields().keySet().contains(JsonMessageConstants.MESSAGE_ID)) {
            return Optional.ofNullable(hit.field(JsonMessageConstants.MESSAGE_ID));
        }
        return Optional.empty();
    }

}
