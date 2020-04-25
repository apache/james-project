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
package org.apache.james.quota.search.elasticsearch.events;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.es.DocumentId;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.RoutingKey;
import org.apache.james.core.Username;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class ElasticSearchQuotaMailboxListener implements MailboxListener.ReactiveGroupMailboxListener {
    public static class ElasticSearchQuotaMailboxListenerGroup extends Group {

    }

    private static final Group GROUP = new ElasticSearchQuotaMailboxListenerGroup();

    private final ElasticSearchIndexer indexer;
    private final QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson;
    private final RoutingKey.Factory<Username> routingKeyFactory;

    @Inject
    public ElasticSearchQuotaMailboxListener(@Named(QuotaRatioElasticSearchConstants.InjectionNames.QUOTA_RATIO) ElasticSearchIndexer indexer,
                                             QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson,
                                             RoutingKey.Factory<Username> routingKeyFactory) {
        this.indexer = indexer;
        this.quotaRatioToElasticSearchJson = quotaRatioToElasticSearchJson;
        this.routingKeyFactory = routingKeyFactory;
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
        Username user = event.getUsername();
        DocumentId id = toDocumentId(user);
        RoutingKey routingKey = routingKeyFactory.from(user);

        return Mono.fromCallable(() -> quotaRatioToElasticSearchJson.convertToJson(event))
            .flatMap(json -> indexer.index(id, json, routingKey))
            .then();
    }

    private DocumentId toDocumentId(Username user) {
        return DocumentId.fromString(user.asString());
    }
}
