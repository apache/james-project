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

import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.core.User;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;

import com.fasterxml.jackson.core.JsonProcessingException;

public class ElasticSearchQuotaMailboxListener implements MailboxListener.GroupMailboxListener {
    public static class ElasticSearchQuotaMailboxListenerGroup extends Group {
    }

    private static final Group GROUP = new ElasticSearchQuotaMailboxListenerGroup();

    private final ElasticSearchIndexer indexer;
    private final QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson;

    @Inject
    public ElasticSearchQuotaMailboxListener(
        @Named(QuotaRatioElasticSearchConstants.InjectionNames.QUOTA_RATIO) ElasticSearchIndexer indexer,
        QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson) {
        this.indexer = indexer;
        this.quotaRatioToElasticSearchJson = quotaRatioToElasticSearchJson;
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
    public void event(Event event) throws JsonProcessingException {
        handleEvent(event.getUser(), (QuotaUsageUpdatedEvent) event);
    }

    private void handleEvent(User user, QuotaUsageUpdatedEvent event) throws JsonProcessingException {
        indexer.index(user.asString(),
            quotaRatioToElasticSearchJson.convertToJson(user.asString(), event));
    }
}
