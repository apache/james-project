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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.james.backends.es.search.ScrollIterable;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.query.SortConverter;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.util.OptionalConverter;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearcher.class);
    private static final TimeValue TIMEOUT = new TimeValue(60000);
    public static final int DEFAULT_SIZE = 100;

    private final Client client;
    private final QueryConverter queryConverter;
    private final int size;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this(client, queryConverter, DEFAULT_SIZE, mailboxIdFactory, messageIdFactory);
    }

    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, int size, MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
    }
    
    public Stream<MessageSearchIndex.SearchResult> search(List<User> users, MultimailboxesSearchQuery query, Optional<Long> limit) throws MailboxException {
        Stream<MessageSearchIndex.SearchResult> pairStream = new ScrollIterable(client, getSearchRequestBuilder(client, users, query, limit)).stream()
            .flatMap(this::transformResponseToUidStream);
        return limit.map(pairStream::limit)
            .orElse(pairStream);
    }
    
    private SearchRequestBuilder getSearchRequestBuilder(Client client, List<User> users, MultimailboxesSearchQuery query, Optional<Long> limit) {
        return query.getSearchQuery().getSorts()
            .stream()
            .reduce(
                client.prepareSearch(MailboxElasticsearchConstants.MAILBOX_INDEX.getValue())
                    .setTypes(MailboxElasticsearchConstants.MESSAGE_TYPE.getValue())
                    .setScroll(TIMEOUT)
                    .addFields(JsonMessageConstants.UID, JsonMessageConstants.MAILBOX_ID, JsonMessageConstants.MESSAGE_ID)
                    .setQuery(queryConverter.from(users, query))
                    .setSize(computeRequiredSize(limit)),
                (searchBuilder, sort) -> searchBuilder.addSort(SortConverter.convertSort(sort)),
                (partialResult1, partialResult2) -> partialResult1);
    }

    private int computeRequiredSize(Optional<Long> limit) {
        return limit.map(value -> Math.min(value.intValue(), size))
            .orElse(size);
    }

    private Stream<MessageSearchIndex.SearchResult> transformResponseToUidStream(SearchResponse searchResponse) {
        return StreamSupport.stream(searchResponse.getHits().spliterator(), false)
            .map(this::extractContentFromHit)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    private Optional<MessageSearchIndex.SearchResult> extractContentFromHit(SearchHit hit) {
        SearchHitField mailboxId = hit.field(JsonMessageConstants.MAILBOX_ID);
        SearchHitField uid = hit.field(JsonMessageConstants.UID);
        Optional<SearchHitField> id = retrieveMessageIdField(hit);
        if (mailboxId != null && uid != null) {
            Number uidAsNumber = uid.getValue();
            return Optional.of(
                new MessageSearchIndex.SearchResult(
                    OptionalConverter.toGuava(id.map(field -> messageIdFactory.fromString(field.getValue()))),
                    mailboxIdFactory.fromString(mailboxId.getValue()),
                    MessageUid.of(uidAsNumber.longValue())));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result " + hit.getId());
            return Optional.empty();
        }
    }

    private Optional<SearchHitField> retrieveMessageIdField(SearchHit hit) {
        if (hit.fields().keySet().contains(JsonMessageConstants.MESSAGE_ID)) {
            return Optional.ofNullable(hit.field(JsonMessageConstants.MESSAGE_ID));
        } else {
            return Optional.empty();
        }
    }

}
