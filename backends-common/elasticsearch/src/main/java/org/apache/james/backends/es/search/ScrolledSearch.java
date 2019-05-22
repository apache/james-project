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

package org.apache.james.backends.es.search;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.james.backends.es.ListenerToFuture;
import org.apache.james.util.streams.Iterators;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;

import com.github.fge.lambdas.Throwing;

public class ScrolledSearch {
    private static class ScrollIterator implements Iterator<SearchResponse>, Closeable {
        private final RestHighLevelClient client;
        private CompletableFuture<SearchResponse> searchResponseFuture;

        ScrollIterator(RestHighLevelClient client, SearchRequest searchRequest) {
            this.client = client;
            ListenerToFuture<SearchResponse> listener = new ListenerToFuture<>();
            client.searchAsync(searchRequest, listener);

            this.searchResponseFuture = listener.getFuture();
        }

        @Override
        public void close() throws IOException {
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(searchResponseFuture.join().getScrollId());
            client.clearScroll(clearScrollRequest);
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
            client.searchScrollAsync(
                new SearchScrollRequest()
                    .scrollId(result.getScrollId())
                    .scroll(TIMEOUT),
                listener);
            searchResponseFuture = listener.getFuture();
            return result;
        }

        public Stream<SearchResponse> stream() {
            return Iterators.toStream(this)
                .onClose(Throwing.runnable(this::close));
        }

        private boolean allSearchResponsesConsumed(SearchResponse searchResponse) {
            return searchResponse.getHits().getHits().length == 0;
        }
    }

    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);

    private final RestHighLevelClient client;
    private final SearchRequest searchRequest;

    public ScrolledSearch(RestHighLevelClient client, SearchRequest searchRequest) {
        this.client = client;
        this.searchRequest = searchRequest;
    }

    public Stream<SearchHit> searchHits() {
        return searchResponses()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits().getHits()));
    }

    public Stream<SearchResponse> searchResponses() {
        return new ScrollIterator(client, searchRequest)
            .stream();
    }
}
