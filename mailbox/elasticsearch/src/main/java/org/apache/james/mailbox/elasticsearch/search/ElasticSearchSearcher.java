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

import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.query.SortConverter;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
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
    private final Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this(client, queryConverter, DEFAULT_SIZE, mailboxIdFactory, messageIdFactory);
    }

    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, int size, Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
    }
    
    public Stream<MessageSearchIndex.SearchResult> search(List<User> users, MultimailboxesSearchQuery query, Optional<Long> limit) throws MailboxException {
        Stream<MessageSearchIndex.SearchResult> pairStream = new ScrollIterable(client, getSearchRequestBuilder(client, users, query)).stream()
            .flatMap(this::transformResponseToUidStream);
        return limit.map(pairStream::limit)
            .orElse(pairStream);
    }
    
    private SearchRequestBuilder getSearchRequestBuilder(Client client, List<User> users, MultimailboxesSearchQuery query) {
        return query.getSearchQuery().getSorts()
            .stream()
            .reduce(
                client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setScroll(TIMEOUT)
                    .addFields(JsonMessageConstants.UID, JsonMessageConstants.MAILBOX_ID, JsonMessageConstants.MESSAGE_ID)
                    .setQuery(queryConverter.from(users, query))
                    .setSize(size),
                (searchBuilder, sort) -> searchBuilder.addSort(SortConverter.convertSort(sort)),
                (partialResult1, partialResult2) -> partialResult1);
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
        SearchHitField id = hit.field(JsonMessageConstants.ID);
        if (mailboxId != null && uid != null && id != null) {
            Number uidAsNumber = uid.getValue();
            return Optional.of(
                new MessageSearchIndex.SearchResult(messageIdFactory.fromString(id.getValue()),
                    mailboxIdFactory.fromString(mailboxId.getValue()),
                    MessageUid.of(uidAsNumber.longValue())));
        } else {
            LOGGER.warn("Can not extract UID, MessageID and/or MailboxId for search result " + hit.getId());
            return Optional.empty();
        }
    }

}
