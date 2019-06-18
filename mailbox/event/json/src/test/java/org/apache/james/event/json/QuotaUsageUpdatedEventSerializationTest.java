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

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Test;

class QuotaUsageUpdatedEventSerializationTest {
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("foo", Optional.empty());
    private static final Quota<QuotaCount> QUOTA_COUNT = Quota.<QuotaCount>builder()
        .used(QuotaCount.count(12))
        .computedLimit(QuotaCount.count(100))
        .build();
    private static final Quota<QuotaSize> QUOTA_SIZE = Quota.<QuotaSize>builder()
        .used(QuotaSize.size(1234))
        .computedLimit(QuotaSize.size(10000))
        .build();
    private static final Instant INSTANT = Instant.parse("2018-11-13T12:00:55Z");
    private final MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsername = new MailboxListener.QuotaUsageUpdatedEvent(
        EVENT_ID,
        User.fromUsername("onlyUsername"),
        QUOTA_ROOT,
        QUOTA_COUNT,
        QUOTA_SIZE,
        INSTANT);
    private final String quotaUsageUpdatedEvent =
        "{" +
        "    \"QuotaUsageUpdatedEvent\":{" +
        "        \"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "        \"quotaRoot\":\"foo\"," +
        "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
        "        \"time\":\"2018-11-13T12:00:55Z\"," +
        "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
        "        \"user\":\"onlyUsername\"" +
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
            "        \"quotaRoot\":\"foo\"," +
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
            "        \"quotaRoot\":\"foo\"," +
            "        \"time\":\"2018-11-13T12:00:55Z\"," +
            "        \"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "        \"user\":\"onlyUsername\"" +
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
            "        \"quotaRoot\":\"foo\"," +
            "        \"time\":\"2018-11-13T12:00:55Z\"," +
            "        \"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
            "        \"user\":\"onlyUsername\"" +
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
            "        \"quotaRoot\":\"foo\"," +
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
            "        \"quotaRoot\":\"foo\"," +
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
