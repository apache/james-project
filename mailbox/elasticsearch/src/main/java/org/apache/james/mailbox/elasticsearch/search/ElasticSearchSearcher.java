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

import java.util.Iterator;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.query.SortConverter;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchSearcher<Id extends MailboxId> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearcher.class);

    private final ClientProvider clientProvider;
    private final QueryConverter queryConverter;

    public ElasticSearchSearcher(ClientProvider clientProvider, QueryConverter queryConverter) {
        this.clientProvider = clientProvider;
        this.queryConverter = queryConverter;
    }

    public Iterator<Long> search(Mailbox<Id> mailbox, SearchQuery searchQuery) throws MailboxException {
        try (Client client = clientProvider.get()) {
            return transformResponseToUidIterator(getSearchRequestBuilder(client, mailbox, searchQuery)
                .get()
            );
        }
    }

    private SearchRequestBuilder getSearchRequestBuilder(Client client, Mailbox<Id> mailbox, SearchQuery searchQuery) {
        return searchQuery.getSorts()
            .stream()
            .reduce(
                client.prepareSearch(ElasticSearchIndexer.MAILBOX_INDEX)
                    .setTypes(ElasticSearchIndexer.MESSAGE_TYPE)
                    .setScroll(new TimeValue(60000))
                    .setQuery(queryConverter.from(searchQuery, mailbox.getMailboxId().serialize()))
                    .setSize(100),
                (searchBuilder, sort) -> searchBuilder.addSort(SortConverter.convertSort(sort)),
                (partialResult1, partialResult2) -> partialResult1);
    }

    private Iterator<Long> transformResponseToUidIterator(SearchResponse searchResponse) {
        return StreamSupport.stream(searchResponse.getHits().spliterator(), false)
            .map(this::extractUidFromHit)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .iterator();

    }

    private Optional<Long> extractUidFromHit(SearchHit hit) {
        try {
            return Optional.of(((Number) hit.getSource().get(JsonMessageConstants.ID)).longValue());
        } catch (Exception exception) {
            LOGGER.warn("Can not extract UID for search result " + hit.getId(), exception);
            return Optional.empty();
        }
    }

}
