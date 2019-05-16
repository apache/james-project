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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.james.util.streams.Iterators;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;

public class ScrollIterable implements Iterable<SearchResponse> {
    private static final TimeValue TIMEOUT = new TimeValue(60000);

    private final RestHighLevelClient client;
    private final SearchRequest searchRequest;

    public ScrollIterable(RestHighLevelClient client, SearchRequest searchRequest) {
        this.client = client;
        this.searchRequest = searchRequest;
    }

    @Override
    public Iterator<SearchResponse> iterator() {
        return new ScrollIterator(client, searchRequest);
    }

    public Stream<SearchResponse> stream() {
        return Iterators.toStream(iterator());
    }

    public static class ScrollIterator implements Iterator<SearchResponse> {
        private final RestHighLevelClient client;
        private CompletableFuture<SearchResponse> searchResponseFuture;

        ScrollIterator(RestHighLevelClient client, SearchRequest searchRequest) {
            this.client = client;
            ListenerToFuture<SearchResponse> listener = new ListenerToFuture<>();
            client.searchAsync(searchRequest, RequestOptions.DEFAULT, listener);

            this.searchResponseFuture = listener.getFuture();
        }

        @Override
        public boolean hasNext() {
            SearchResponse join = searchResponseFuture.join();
            return !allSearchResponsesConsumed(join);
        }

        @Override
        public SearchResponse next() {
            SearchResponse result = searchResponseFuture.join();
            ListenerToFuture<SearchResponse> listener = new ListenerToFuture<>();
            client.scrollAsync(
                new SearchScrollRequest()
                    .scrollId(result.getScrollId())
                    .scroll(TIMEOUT),
                RequestOptions.DEFAULT,
                listener);
            searchResponseFuture = listener.getFuture();
            return result;
        }

        private boolean allSearchResponsesConsumed(SearchResponse searchResponse) {
            return searchResponse.getHits().getHits().length == 0;
        }
    }

}
