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

package org.apache.james.quota.search.elasticsearch.json;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.events.MailboxListener.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.model.QuotaRatio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class QuotaRatioToElasticSearchJson {

    private final ObjectMapper mapper;

    @Inject
    public QuotaRatioToElasticSearchJson() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new Jdk8Module());
    }

    public String convertToJson(QuotaUsageUpdatedEvent event) throws JsonProcessingException {
        return mapper.writeValueAsString(QuotaRatioAsJson.builder()
                .user(event.getUser().asString())
                .domain(event.getQuotaRoot().getDomain().map(Domain::asString))
                .quotaRatio(QuotaRatio.from(event.getSizeQuota(), event.getCountQuota()))
                .build());
    }
}
