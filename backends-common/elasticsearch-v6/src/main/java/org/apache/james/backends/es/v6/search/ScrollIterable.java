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

package org.apache.james.backends.es.v6.search;

import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.james.util.streams.Iterators;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;

public class ScrollIterable implements Iterable<SearchResponse> {

    private static final TimeValue TIMEOUT = new TimeValue(60000);
    private final Client client;
    private final SearchRequestBuilder searchRequestBuilder;

    public ScrollIterable(Client client, SearchRequestBuilder searchRequestBuilder) {
        this.client = client;
        this.searchRequestBuilder = searchRequestBuilder;
    }

    @Override
    public Iterator<SearchResponse> iterator() {
        return new ScrollIterator(client, searchRequestBuilder);
    }

    public Stream<SearchResponse> stream() {
        return Iterators.toStream(iterator());
    }

    public static class ScrollIterator implements Iterator<SearchResponse> {

        private final Client client;
        private ListenableActionFuture<SearchResponse> searchResponseFuture;

        public ScrollIterator(Client client, SearchRequestBuilder searchRequestBuilder) {
            this.client = client;
            this.searchResponseFuture = searchRequestBuilder.execute();
        }

        @Override
        public boolean hasNext() {
            return !allSearchResponsesConsumed(searchResponseFuture.actionGet());
        }

        @Override
        public SearchResponse next() {
            SearchResponse result = searchResponseFuture.actionGet();
            searchResponseFuture =  client.prepareSearchScroll(result.getScrollId())
                .setScroll(TIMEOUT)
                .execute();
            return result;
        }

        private boolean allSearchResponsesConsumed(SearchResponse searchResponse) {
            return searchResponse.getHits().getHits().length == 0;
        }
    }

}
