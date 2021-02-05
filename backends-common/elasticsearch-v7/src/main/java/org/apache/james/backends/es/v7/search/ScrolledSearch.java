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

package org.apache.james.backends.es.v7.search;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
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
            .concatMap(searchResponse -> Flux.just(searchResponse.getHits().getHits()));
    }

    public Flux<SearchResponse> searchResponses() {
        return Flux.push(sink -> {
            AtomicReference<Optional<String>> scrollId = new AtomicReference<>(Optional.empty());
            sink.onRequest(numberOfRequestedElements -> next(sink, scrollId, numberOfRequestedElements));

            sink.onDispose(() -> close(scrollId));
        });
    }

    private void next(FluxSink<SearchResponse> sink, AtomicReference<Optional<String>> scrollId, long numberOfRequestedElements) {
        if (numberOfRequestedElements <= 0) {
            return;
        }

        Consumer<SearchResponse> onResponse = searchResponse -> {
            scrollId.set(Optional.of(searchResponse.getScrollId()));
            sink.next(searchResponse);

            boolean noHitsLeft = searchResponse.getHits().getHits().length == 0;
            if (noHitsLeft) {
                sink.complete();
            } else {
                next(sink, scrollId, numberOfRequestedElements - 1);
            }
        };

        Consumer<Throwable> onFailure = sink::error;

        buildRequest(scrollId.get())
            .subscribe(onResponse, onFailure);
    }

    private Mono<SearchResponse> buildRequest(Optional<String> scrollId) {
        return scrollId.map(id ->
            client.scroll(
                new SearchScrollRequest()
                    .scrollId(scrollId.get())
                    .scroll(TIMEOUT),
                RequestOptions.DEFAULT))
            .orElseGet(() -> client.search(searchRequest, RequestOptions.DEFAULT));
    }

    public void close(AtomicReference<Optional<String>> scrollId) {
        scrollId.get().map(id -> {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(id);
                return clearScrollRequest;
            }).ifPresent(Throwing.<ClearScrollRequest>consumer(clearScrollRequest -> client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT).subscribe()).sneakyThrow());
    }

}
