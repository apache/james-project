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
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class ElasticSearchQuotaMailboxListener implements MailboxListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchQuotaMailboxListener.class);

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
    public ListenerType getType() {
        return ListenerType.ONCE;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public void event(Event event) {
        try {
            if (event instanceof QuotaUsageUpdatedEvent) {
                handleEvent(getUser(event), (QuotaUsageUpdatedEvent) event);
            }
        } catch (Exception e) {
            LOGGER.error("Can not index quota ratio", e);
        }
    }

    private void handleEvent(User user, QuotaUsageUpdatedEvent event) throws JsonProcessingException {
        indexer.index(user.getUserName(), 
                quotaRatioToElasticSearchJson.convertToJson(user.getUserName(), event));
    }

    private User getUser(Event event) {
        return event.getSession()
                .getUser();
    }
}
