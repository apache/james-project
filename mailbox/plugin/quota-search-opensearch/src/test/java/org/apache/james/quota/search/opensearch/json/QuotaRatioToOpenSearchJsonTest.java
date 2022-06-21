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
package org.apache.james.quota.search.opensearch.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Instant;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.quota.QuotaFixture;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class QuotaRatioToOpenSearchJsonTest {
    static Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");

    @Test
    void quotaRatioShouldBeWellConvertedToJson() throws IOException {
        String user = "user@domain.org";
        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(mock(SessionProvider.class), mock(MailboxSessionMapperFactory.class));

        Username username = Username.of(user);
        QuotaUsageUpdatedEvent event = EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(username)
            .quotaRoot(quotaRootResolver.forUser(username))
            .quotaCount(QuotaFixture.Counts._52_PERCENT)
            .quotaSize(QuotaFixture.Sizes._55_PERCENT)
            .instant(Instant.now())
            .build();
        QuotaRatioToOpenSearchJson quotaRatioToOpenSearchJson = new QuotaRatioToOpenSearchJson(
            quotaRootResolver);
        String convertToJson = quotaRatioToOpenSearchJson.convertToJson(event);

        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("quotaRatio.json"));
    }

    @Test
    void quotaRatioShouldBeWellConvertedToJsonWhenNoDomain() throws IOException {
        String user = "user";
        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(mock(SessionProvider.class), mock(MailboxSessionMapperFactory.class));
        Username username = Username.of(user);
        QuotaUsageUpdatedEvent event = EventFactory.quotaUpdated()
            .eventId(EVENT_ID)
            .user(username)
            .quotaRoot(quotaRootResolver.forUser(username))
            .quotaCount(QuotaFixture.Counts._52_PERCENT)
            .quotaSize(QuotaFixture.Sizes._55_PERCENT)
            .instant(Instant.now())
            .build();

        QuotaRatioToOpenSearchJson quotaRatioToOpenSearchJson = new QuotaRatioToOpenSearchJson(quotaRootResolver);
        String convertToJson = quotaRatioToOpenSearchJson.convertToJson(event);

        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("quotaRatioNoDomain.json"));
    }
}
