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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.query.SortConverter;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.Multimap;

public class ElasticSearchSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearcher.class);
    private static final TimeValue TIMEOUT = new TimeValue(60000);
    public static final int DEFAULT_SIZE = 100;

    private final Client client;
    private final QueryConverter queryConverter;
    private final int size;
    private final Factory mailboxIdFactory;

    @Inject
    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, MailboxId.Factory mailboxIdFactory) {
        this(client, queryConverter, DEFAULT_SIZE, mailboxIdFactory);
    }

    public ElasticSearchSearcher(Client client, QueryConverter queryConverter, int size, MailboxId.Factory mailboxIdFactory) {
        this.client = client;
        this.queryConverter = queryConverter;
        this.size = size;
        this.mailboxIdFactory = mailboxIdFactory;
    }
    
    public Multimap<MailboxId, MessageUid> search(List<User> users, MultimailboxesSearchQuery query) throws MailboxException {
        return new ScrollIterable(client, getSearchRequestBuilder(client, users, query)).stream()
            .flatMap(this::transformResponseToUidStream)
            .collect(Guavate.toImmutableListMultimap(Pair::getLeft, Pair::getRight));
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

    private Stream<Pair<MailboxId, MessageUid>> transformResponseToUidStream(SearchResponse searchResponse) {
        return StreamSupport.stream(searchResponse.getHits().spliterator(), false)
            .map(this::extractContentFromHit)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    private Optional<Pair<MailboxId, MessageUid>> extractContentFromHit(SearchHit hit) {
        SearchHitField mailboxId = hit.field(JsonMessageConstants.MAILBOX_ID);
        SearchHitField uid = hit.field(JsonMessageConstants.UID);
        if (mailboxId != null && uid != null) {
            Number uidAsNumber = uid.getValue();
            return Optional.of(Pair.of(mailboxIdFactory.fromString(mailboxId.getValue()), MessageUid.of(uidAsNumber.longValue())));
        } else {
            LOGGER.warn("Can not extract UID and/or MailboxId for search result " + hit.getId());
            return Optional.empty();
        }
    }

}
