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

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.MailboxListener.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaFixture;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

public class QuotaRatioToElasticSearchJsonTest {

    @Test
    public void quotaRatioShouldBeWellConvertedToJson() throws IOException {
        String user = "user@domain.org";
        QuotaUsageUpdatedEvent event = new QuotaUsageUpdatedEvent(
                new MockMailboxSession(user), 
                QuotaRoot.quotaRoot("any", Optional.of(Domain.of("domain.org"))),
                QuotaFixture.Counts._52_PERCENT,
                QuotaFixture.Sizes._55_PERCENT,
                Instant.now());
        

        QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson = new QuotaRatioToElasticSearchJson();
        String convertToJson = quotaRatioToElasticSearchJson.convertToJson(user, event);

        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("quotaRatio.json"));
    }

    @Test
    public void quotaRatioShouldBeWellConvertedToJsonWhenNoDomain() throws IOException {
        String user = "user";
        QuotaUsageUpdatedEvent event = new QuotaUsageUpdatedEvent(
                new MockMailboxSession(user),
                QuotaRoot.quotaRoot("any", Optional.empty()),
                QuotaFixture.Counts._52_PERCENT,
                QuotaFixture.Sizes._55_PERCENT,
                Instant.now());


        QuotaRatioToElasticSearchJson quotaRatioToElasticSearchJson = new QuotaRatioToElasticSearchJson();
        String convertToJson = quotaRatioToElasticSearchJson.convertToJson(user, event);

        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("quotaRatioNoDomain.json"));
    }
}
