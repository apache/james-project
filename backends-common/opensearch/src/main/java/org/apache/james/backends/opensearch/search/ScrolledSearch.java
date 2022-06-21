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

package org.apache.james.backends.opensearch.search;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public class ScrolledSearch {
    private static final Time TIMEOUT = new Time.Builder()
        .time("1m")
        .build();

    private final ReactorElasticSearchClient client;
    private final SearchRequest searchRequest;

    public ScrolledSearch(ReactorElasticSearchClient client, SearchRequest searchRequest) {
        this.client = client;
        this.searchRequest = searchRequest;
    }

    public Flux<Hit<ObjectNode>> searchHits() {
        return searchResponses()
            .concatMap(searchResponse -> Flux.fromIterable(searchResponse.hits().hits()));
    }

    private Flux<ScrollResponse<ObjectNode>> searchResponses() {
        return Flux.push(sink -> {
            AtomicReference<Optional<String>> scrollId = new AtomicReference<>(Optional.empty());
            sink.onRequest(numberOfRequestedElements -> next(sink, scrollId, numberOfRequestedElements));

            sink.onDispose(() -> close(scrollId));
        });
    }

    private void next(FluxSink<ScrollResponse<ObjectNode>> sink, AtomicReference<Optional<String>> scrollId, long numberOfRequestedElements) {
        if (numberOfRequestedElements <= 0) {
            return;
        }

        Consumer<ScrollResponse<ObjectNode>> onResponse = searchResponse -> {
            scrollId.set(Optional.of(searchResponse.scrollId()));
            sink.next(searchResponse);

            if (searchResponse.hits().hits().isEmpty()) {
                sink.complete();
            } else {
                next(sink, scrollId, numberOfRequestedElements - 1);
            }
        };

        Consumer<Throwable> onFailure = sink::error;

        buildRequest(scrollId.get())
            .subscribe(onResponse, onFailure);
    }

    private Mono<ScrollResponse<ObjectNode>> buildRequest(Optional<String> scrollId) {
        return scrollId.map(Throwing.function(id -> client.scroll(new ScrollRequest.Builder()
                            .scrollId(scrollId.get())
                            .scroll(TIMEOUT)
                            .build())).sneakyThrow())
            .orElseGet(() -> {
                try {
                    return client.search(searchRequest)
                        .map(response -> new ScrollResponse.Builder<ObjectNode>()
                            .scrollId(response.scrollId())
                            .hits(response.hits())
                            .took(response.took())
                            .timedOut(response.timedOut())
                            .shards(response.shards())
                            .build());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    public void close(AtomicReference<Optional<String>> scrollId) {
        scrollId.get().map(id -> new ClearScrollRequest.Builder()
                .scrollId(id)
                .build())
            .ifPresent(Throwing.<ClearScrollRequest>consumer(clearScrollRequest ->
                client.clearScroll(clearScrollRequest).subscribe()).sneakyThrow());
    }

}
