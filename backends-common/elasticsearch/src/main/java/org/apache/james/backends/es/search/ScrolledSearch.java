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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ScrolledSearch {
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);

    private final ReactorElasticSearchClient client;
    private final SearchRequest searchRequest;

    public ScrolledSearch(ReactorElasticSearchClient client, SearchRequest searchRequest) {
        this.client = client;
        this.searchRequest = searchRequest;
    }


    public Flux<SearchHit> searchHits() {
        return searchResponses()
            .flatMap(searchResponse -> Flux.fromArray(searchResponse.getHits().getHits()));
    }

    public Flux<SearchResponse> searchResponses() {
        return ensureClosing(Flux.from(startScrolling(searchRequest))
            .expand(this::nextResponse));
    }

    private Mono<SearchResponse> startScrolling(SearchRequest searchRequest) {
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    public Mono<SearchResponse> nextResponse(SearchResponse previous) {
        if (allSearchResponsesConsumed(previous)) {
            return Mono.empty();
        }

        return client.scroll(
            new SearchScrollRequest()
                .scrollId(previous.getScrollId())
                .scroll(TIMEOUT),
            RequestOptions.DEFAULT);
    }

    private boolean allSearchResponsesConsumed(SearchResponse searchResponse) {
        return searchResponse.getHits().getHits().length == 0;
    }

    private Flux<SearchResponse> ensureClosing(Flux<SearchResponse> origin) {
        AtomicReference<SearchResponse> latest = new AtomicReference<>();
        return origin
            .doOnNext(latest::set)
            .doOnTerminate(close(latest));
    }

    public Runnable close(AtomicReference<SearchResponse> latest) {
        return () -> Optional.ofNullable(latest.getAndSet(null)).map(this::clearScroll);
    }

    private Disposable clearScroll(SearchResponse current) {
        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(current.getScrollId());

        return client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT).subscribe();
    }
}
