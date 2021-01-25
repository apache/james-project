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

package org.apache.james.event.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.event.json.SerializerFixture.EVENT_ID;
import static org.apache.james.event.json.SerializerFixture.EVENT_SERIALIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.MailboxEvents.QuotaUsageUpdatedEvent;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Test;

class QuotaUsageUpdatedEventSerializationTest {
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&foo", Optional.empty());
    private static final Quota<QuotaCountLimit, QuotaCountUsage> QUOTA_COUNT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
        .used(QuotaCountUsage.count(12))
        .computedLimit(QuotaCountLimit.count(100))
        .build();
    private static final Quota<QuotaSizeLimit, QuotaSizeUsage> QUOTA_SIZE = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
        .used(QuotaSizeUsage.size(1234))
        .computedLimit(QuotaSizeLimit.size(10000))
        .build();
    private static final Instant INSTANT = Instant.parse("2018-11-13T12:00:55Z");
    private final QuotaUsageUpdatedEvent eventWithUserContainsUsername = new QuotaUsageUpdatedEvent(
        EVENT_ID,
        Username.of("onlyusername"),
        QUOTA_ROOT,
        QUOTA_COUNT,
        QUOTA_SIZE,
        INSTANT);
    private final String quotaUsageUpdatedEvent =
        "{" +
        "    \"QuotaUsageUpdatedEvent\":{" +
        "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "        \"quotaRoot\":\"#private&foo\"," +
        "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
        "        \"time\":\"2018-11-13T12:00:55Z\"," +
        "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
        "        \"user\":\"onlyusername\"" +
        "    }" +
        "}";

    @Test
    void fromJsonShouldReturnQuotaEvent() {
        assertThat(EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isEqualTo(eventWithUserContainsUsername);
    }

    @Test
    void toJsonShouldReturnQuotaEventJson() {
        assertThatJson(EVENT_SERIALIZER.toJson(eventWithUserContainsUsername))
            .isEqualTo(quotaUsageUpdatedEvent);
    }

    @Test
    void fromJsonShouldThrowResultWhenUserIsMissing() {
        String quotaUsageUpdatedEvent =
            "{" +
            "    \"QuotaUsageUpdatedEvent\":{" +
            "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "        \"quotaRoot\":\"#private&foo\"," +
            "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
            "        \"time\":\"2018-11-13T12:00:55Z\"," +
            "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}" +
            "    }" +
            "}";

        assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void fromJsonShouldThrowWhenCountQuotaIsMissing() {
        String quotaUsageUpdatedEvent =
            "{" +
            "    \"QuotaUsageUpdatedEvent\":{" +
            "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "        \"quotaRoot\":\"#private&foo\"," +
            "        \"time\":\"2018-11-13T12:00:55Z\"," +
            "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "        \"user\":\"onlyusername\"" +
            "    }" +
            "}";

        assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void fromJsonShouldThrowWhenSizeQuotaIsMissing() {
        String quotaUsageUpdatedEvent =
            "{" +
            "    \"QuotaUsageUpdatedEvent\":{" +
            "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "        \"quotaRoot\":\"#private&foo\"," +
            "        \"time\":\"2018-11-13T12:00:55Z\"," +
            "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
            "        \"user\":\"onlyusername\"" +
            "    }" +
            "}";

        assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void fromJsonShouldThrowResultWhenTimeIsNull() {
        String quotaUsageUpdatedEvent =
            "{" +
            "    \"QuotaUsageUpdatedEvent\":{" +
            "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "        \"quotaRoot\":\"#private&foo\"," +
            "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
            "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "        \"user\":\"user\"" +
            "    }" +
            "}";

        assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void fromJsonShouldThrowResultWhenTimeIsEmpty() {
        String quotaUsageUpdatedEvent =
            "{" +
            "    \"QuotaUsageUpdatedEvent\":{" +
            "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
            "        \"quotaRoot\":\"#private&foo\"," +
            "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
            "        \"time\":\"\"," +
            "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "        \"user\":\"user\"" +
            "    }" +
            "}";

        assertThatThrownBy(() -> EVENT_SERIALIZER.fromJson(quotaUsageUpdatedEvent).get())
            .isInstanceOf(NoSuchElementException.class);
    }
}
