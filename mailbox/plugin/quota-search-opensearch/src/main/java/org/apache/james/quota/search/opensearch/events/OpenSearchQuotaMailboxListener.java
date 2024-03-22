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
package org.apache.james.quota.search.opensearch.events;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.opensearch.DocumentId;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.quota.search.opensearch.QuotaRatioOpenSearchConstants;
import org.apache.james.quota.search.opensearch.json.QuotaRatioToOpenSearchJson;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class OpenSearchQuotaMailboxListener implements EventListener.ReactiveGroupEventListener {
    public static class OpenSearchQuotaMailboxListenerGroup extends Group {

    }

    private static final Group GROUP = new OpenSearchQuotaMailboxListenerGroup();

    private final OpenSearchIndexer indexer;
    private final QuotaRatioToOpenSearchJson quotaRatioToOpenSearchJson;
    private final RoutingKey.Factory<Username> routingKeyFactory;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public OpenSearchQuotaMailboxListener(@Named(QuotaRatioOpenSearchConstants.InjectionNames.QUOTA_RATIO) OpenSearchIndexer indexer,
                                          QuotaRatioToOpenSearchJson quotaRatioToOpenSearchJson,
                                          RoutingKey.Factory<Username> routingKeyFactory, QuotaRootResolver quotaRootResolver) {
        this.indexer = indexer;
        this.quotaRatioToOpenSearchJson = quotaRatioToOpenSearchJson;
        this.routingKeyFactory = routingKeyFactory;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof QuotaUsageUpdatedEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        return handleEvent((QuotaUsageUpdatedEvent) event);
    }

    private Mono<Void> handleEvent(QuotaUsageUpdatedEvent event) {
        Username user = quotaRootResolver.associatedUsername(event.getQuotaRoot());
        DocumentId id = toDocumentId(user);
        RoutingKey routingKey = routingKeyFactory.from(user);

        return Mono.fromCallable(() -> quotaRatioToOpenSearchJson.convertToJson(event))
            .flatMap(json -> indexer.index(id, json, routingKey))
            .then();
    }

    private DocumentId toDocumentId(Username user) {
        return DocumentId.fromString(user.asString());
    }
}
