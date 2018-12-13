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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuotaUsageUpdatedEventSerializationTest {

    private static final User USER = User.fromUsername("user");
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
    private static final MailboxListener.QuotaUsageUpdatedEvent DEFAULT_QUOTA_EVENT =
        new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, QUOTA_COUNT, QUOTA_SIZE, INSTANT);

    private static final String DEFAULT_QUOTA_EVENT_JSON =
        "{" +
            "\"QuotaUsageUpdatedEvent\":{" +
            "\"quotaRoot\":\"foo\"," +
            "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
            "\"time\":\"2018-11-13T12:00:55Z\"," +
            "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
            "\"user\":\"user\"" +
            "}" +
        "}";

    private static final EventSerializer$ QUOTA_EVENT_MODULE = EventSerializer$.MODULE$;

    @Nested
    class WithUser {

        @Nested
        class WithValidUser {

            @Nested
            class WithUserContainsOnlyUsername {

                private final MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsername = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("onlyUsername"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private final String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"onlyUsername\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsername);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QUOTA_EVENT_MODULE.toJson(eventWithUserContainsUsername))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }

            @Nested
            class WithUserContainsUsernameAndDomain {

                private final MailboxListener.QuotaUsageUpdatedEvent eventWithUserContainsUsernameAndDomain = new MailboxListener.QuotaUsageUpdatedEvent(
                    User.fromUsername("user@domain"),
                    QUOTA_ROOT,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
                private final String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user@domain\"" +
                        "}" +
                    "}";

                @Test
                void fromJsonShouldReturnQuotaEvent() {
                    assertThat(QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                        .isEqualTo(eventWithUserContainsUsernameAndDomain);
                }

                @Test
                void toJsonShouldReturnQuotaEventJson() {
                    assertThatJson(QUOTA_EVENT_MODULE.toJson(eventWithUserContainsUsernameAndDomain))
                        .isEqualTo(quotaUsageUpdatedEvent);
                }
            }
        }

        @Nested
        class WithInvalidUser {

            @Test
            void fromJsonShouldThrowWhenEmptyUser() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }


            @Test
            void fromJsonShouldThrowResultWhenUserIsNull() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}" +
                        "}" +
                    "}";

                assertThatThrownBy(() ->QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                    .isInstanceOf(NoSuchElementException.class);
            }

            @Test
            void fromJsonShouldThrowWhenUserIsInvalid() {
                String quotaUsageUpdatedEvent =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"@domain\"" +
                        "}" +
                    "}";
                assertThatThrownBy(() -> QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

    }

    @Nested
    class WitQuotaRoot {

        @Nested
        class WithNormalQuotaRoot {

            @Test
            void toJsonShouldReturnSerializedJsonQuotaRoot() {
                assertThatJson(QUOTA_EVENT_MODULE.toJson(DEFAULT_QUOTA_EVENT))
                    .isEqualTo(DEFAULT_QUOTA_EVENT_JSON);
            }

            @Test
            void fromJsonShouldDeserializeQuotaRootJson() {
                assertThat(QUOTA_EVENT_MODULE.fromJson(DEFAULT_QUOTA_EVENT_JSON).get())
                    .isEqualTo(DEFAULT_QUOTA_EVENT);
            }
        }

        @Nested
        class WithEmptyQuotaRoot {
            private final QuotaRoot emptyQuotaRoot = QuotaRoot.quotaRoot("", Optional.empty());
            private final MailboxListener.QuotaUsageUpdatedEvent eventWithEmptyQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    emptyQuotaRoot,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);
            private final String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldSerializeWithEmptyQuotaRoot() {
                assertThatJson(QUOTA_EVENT_MODULE.toJson(eventWithEmptyQuotaRoot))
                    .isEqualTo(quotaUsageUpdatedEvent);
            }

            @Test
            void fromJsonShouldDeserializeWithEmptyQuotaRoot() {
                assertThat(QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                    .isEqualTo(eventWithEmptyQuotaRoot);
            }
        }

        @Nested
        class WithNullQuotaRoot {
            private final MailboxListener.QuotaUsageUpdatedEvent eventWithNullQuotaRoot =
                new MailboxListener.QuotaUsageUpdatedEvent(
                    USER,
                    null,
                    QUOTA_COUNT,
                    QUOTA_SIZE,
                    INSTANT);

            private final String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                    "\"time\":\"2018-11-13T12:00:55Z\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            @Test
            void toJsonShouldThrowWithNullQuotaRoot() {
                assertThatThrownBy(() -> QUOTA_EVENT_MODULE.toJson(eventWithNullQuotaRoot))
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            void fromJsonShouldThrowWithNullQuotaRoot() {
                assertThatThrownBy(() -> QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                    .isInstanceOf(NoSuchElementException.class);
            }
        }
    }

    
    @Nested
    class WithQuotaCount {

        private MailboxListener.QuotaUsageUpdatedEvent quotaEventByQuotaCount(Quota<QuotaCount> countQuota) {
            return new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, countQuota, QUOTA_SIZE, INSTANT);
        }

        @Nested
        class LimitedQuotaCount {

            private Quota<QuotaCount> limitedQuotaCountByScopes(Quota.Scope scope) {
                return Quota.<QuotaCount>builder()
                    .used(QuotaCount.count(12))
                    .computedLimit(QuotaCount.count(100))
                    .limitForScope(QuotaCount.count(100), scope)
                    .build();
            }

            @Nested
            class LimitedQuotaGlobalScope {

                private final String limitedQuotaCountEventJsonGlobalScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Global\":100}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.Global));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaCountEventJsonGlobalScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.Global));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaCountEventJsonGlobalScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class LimitedQuotaDomainScope {
                private final String limitedQuotaCountEventJsonDomainScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.Domain));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaCountEventJsonDomainScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.Domain));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaCountEventJsonDomainScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class LimitedQuotaUserScope {
                private final String limitedQuotaCountEventJsonUserScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"User\":100}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.User));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaCountEventJsonUserScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(limitedQuotaCountByScopes(Quota.Scope.User));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaCountEventJsonUserScope).get())
                        .isEqualTo(quotaEvent);
                }
            }
        }

        @Nested
        class UnLimitedQuotaCount {

            private Quota<QuotaCount> unLimitedQuotaCountByScopes(Quota.Scope scope) {
                return Quota.<QuotaCount>builder()
                    .used(QuotaCount.count(12))
                    .computedLimit(QuotaCount.unlimited())
                    .limitForScope(QuotaCount.unlimited(), scope)
                    .build();
            }

            @Nested
            class UnLimitedQuotaGlobalScope {
                private final String unLimitedQuotaCountEventJsonGlobalScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":null,\"limits\":{\"Global\":null}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.Global));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaCountEventJsonGlobalScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.Global));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaCountEventJsonGlobalScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class UnLimitedQuotaDomainScope {
                private final String unLimitedQuotaCountEventJsonDomainScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":null,\"limits\":{\"Domain\":null}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.Domain));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaCountEventJsonDomainScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.Domain));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaCountEventJsonDomainScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class UnLimitedQuotaUserScope {
                private final String unLimitedQuotaCountEventJsonUserScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":null,\"limits\":{\"User\":null}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.User));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaCountEventJsonUserScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaCount() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaCount(unLimitedQuotaCountByScopes(Quota.Scope.User));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaCountEventJsonUserScope).get())
                        .isEqualTo(quotaEvent);
                }
            }
        }
    }

    
    @Nested
    class WithQuotaSize {

        private MailboxListener.QuotaUsageUpdatedEvent quotaEventByQuotaSize(Quota<QuotaSize> quotaSize) {
            return new MailboxListener.QuotaUsageUpdatedEvent(USER, QUOTA_ROOT, QUOTA_COUNT, quotaSize, INSTANT);
        }

        @Nested
        class LimitedQuotaSize {

            private Quota<QuotaSize> limitedQuotaSizeByScopes(Quota.Scope scope) {
                return Quota.<QuotaSize>builder()
                    .used(QuotaSize.size(1234))
                    .computedLimit(QuotaSize.size(10000))
                    .limitForScope(QuotaSize.size(10000), scope)
                    .build();
            }

            @Nested
            class LimitedQuotaSizeGlobalScope {

                private final String limitedQuotaSizeEventJsonGlobalScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{\"Global\":10000}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.Global));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaSizeEventJsonGlobalScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.Global));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaSizeEventJsonGlobalScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class LimitedQuotaSizeDomainScope {
                private final String limitedQuotaSizeEventJsonDomainScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{\"Domain\":10000}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.Domain));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaSizeEventJsonDomainScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.Domain));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaSizeEventJsonDomainScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class LimitedQuotaSizeUserScope {
                private final String limitedQuotaSizeEventJsonUserScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{\"User\":10000}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.User));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(limitedQuotaSizeEventJsonUserScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(limitedQuotaSizeByScopes(Quota.Scope.User));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(limitedQuotaSizeEventJsonUserScope).get())
                        .isEqualTo(quotaEvent);
                }
            }
        }

        @Nested
        class UnLimitedQuotaSize {

            private Quota<QuotaSize> unLimitedQuotaSizeByScopes(Quota.Scope scope) {
                return Quota.<QuotaSize>builder()
                    .used(QuotaSize.size(1234))
                    .computedLimit(QuotaSize.unlimited())
                    .limitForScope(QuotaSize.unlimited(), scope)
                    .build();
            }

            @Nested
            class UnLimitedQuotaSizeGlobalScope {

                private final String unLimitedQuotaSizeEventJsonGlobalScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":null,\"limits\":{\"Global\":null}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.Global));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaSizeEventJsonGlobalScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.Global));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaSizeEventJsonGlobalScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class UnLimitedQuotaSizeDomainScope {
                private final String unLimitedQuotaSizeEventJsonDomainScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":null,\"limits\":{\"Domain\":null}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.Domain));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaSizeEventJsonDomainScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.Domain));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaSizeEventJsonDomainScope).get())
                        .isEqualTo(quotaEvent);
                }
            }

            @Nested
            class UnLimitedQuotaSizeUserScope {
                private final String unLimitedQuotaSizeEventJsonUserScope =
                    "{" +
                        "\"QuotaUsageUpdatedEvent\":{" +
                        "\"quotaRoot\":\"foo\"," +
                        "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{}}," +
                        "\"time\":\"2018-11-13T12:00:55Z\"," +
                        "\"sizeQuota\":{\"used\":1234,\"limit\":null,\"limits\":{\"User\":null}}," +
                        "\"user\":\"user\"" +
                        "}" +
                    "}";

                @Test
                void toJsonShouldSerializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.User));

                    assertThatJson(QUOTA_EVENT_MODULE.toJson(quotaEvent))
                        .isEqualTo(unLimitedQuotaSizeEventJsonUserScope);
                }

                @Test
                void fromJsonShouldDeserializeQuotaSize() {
                    MailboxListener.QuotaUsageUpdatedEvent quotaEvent = quotaEventByQuotaSize(unLimitedQuotaSizeByScopes(Quota.Scope.User));

                    assertThat(QUOTA_EVENT_MODULE.fromJson(unLimitedQuotaSizeEventJsonUserScope).get())
                        .isEqualTo(quotaEvent);
                }
            }
        }
    }

    @Nested
    class WithTime {

        @Test
        void toJsonShouldReturnSerializedJsonEventWhenTimeIsValid() {
            assertThatJson(QUOTA_EVENT_MODULE.toJson(DEFAULT_QUOTA_EVENT))
                .isEqualTo(DEFAULT_QUOTA_EVENT_JSON);
        }

        @Test
        void fromJsonShouldReturnDeSerializedEventWhenTimeIsValid() {
            assertThat(QUOTA_EVENT_MODULE.fromJson(DEFAULT_QUOTA_EVENT_JSON).get())
                .isEqualTo(DEFAULT_QUOTA_EVENT);
        }

        @Test
        void fromJsonShouldThrowResultWhenTimeIsNull() {
            String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            assertThatThrownBy(() -> QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void fromJsonShouldThrowResultWhenTimeIsEmpty() {
            String quotaUsageUpdatedEvent =
                "{" +
                    "\"QuotaUsageUpdatedEvent\":{" +
                    "\"quotaRoot\":\"foo\"," +
                    "\"countQuota\":{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}," +
                    "\"time\":\"\"," +
                    "\"sizeQuota\":{\"used\":1234,\"limit\":10000,\"limits\":{}}," +
                    "\"user\":\"user\"" +
                    "}" +
                "}";

            assertThatThrownBy(() -> QUOTA_EVENT_MODULE.fromJson(quotaUsageUpdatedEvent).get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
