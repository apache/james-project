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

package org.apache.james.event.json.dtos;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.event.json.SerializerFixture.DTO_JSON_SERIALIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.event.json.DTOs;
import org.apache.james.mailbox.model.Quota;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.JsNull$;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsString;
import play.api.libs.json.Json;
import scala.math.BigDecimal;

class QuotaCountTest {
    @Test
    void quotaCountLimitShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaLimitValueWrites().writes(QuotaCountLimit.count(18)))
            .isEqualTo(new JsNumber(BigDecimal.valueOf(18)));
    }

    @Test
    void quotaCountLimitShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaCountLimitReads().reads(new JsNumber(BigDecimal.valueOf(18))).get())
            .isEqualTo(QuotaCountLimit.count(18));
    }

    @Test
    void quotaCountLimitShouldBeWellSerializedWhenUnlimited() {
        assertThat(DTO_JSON_SERIALIZE.quotaLimitValueWrites().writes(QuotaCountLimit.unlimited()))
            .isEqualTo(JsNull$.MODULE$);
    }

    @Test
    void quotaCountLimitShouldBeWellDeSerializedWhenUnlimited() {
        assertThat(DTO_JSON_SERIALIZE.quotaCountLimitReads().reads(JsNull$.MODULE$).get())
            .isEqualTo(QuotaCountLimit.unlimited());
    }

    @Test
    void quotaCountLimitShouldReturnErrorWhenString() {
        assertThat(DTO_JSON_SERIALIZE.quotaCountLimitReads().reads(new JsString("18")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void quotaCountUsageShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaUsageValueWrites().writes(QuotaCountUsage.count(18)))
            .isEqualTo(new JsNumber(BigDecimal.valueOf(18)));
    }

    @Test
    void quotaCountUsageShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaCountUsageReads().reads(new JsNumber(BigDecimal.valueOf(18))).get())
            .isEqualTo(QuotaCountUsage.count(18));
    }

    @Test
    void quotaCountUsageShouldReturnErrorWhenString() {
        assertThat(DTO_JSON_SERIALIZE.quotaCountUsageReads().reads(new JsString("18")))
            .isInstanceOf(JsError.class);
    }

    @Nested
    class LimitedQuotaCount {
        private Quota<QuotaCountLimit, QuotaCountUsage> limitedQuotaCountByScopes(Quota.Scope scope) {
            return Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                .used(QuotaCountUsage.count(12))
                .computedLimit(QuotaCountLimit.count(100))
                .limitForScope(QuotaCountLimit.count(100), scope)
                .build();
        }

        @Nested
        class LimitedQuotaGlobalScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"Global\":100}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = limitedQuotaCountByScopes(Quota.Scope.Global);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class LimitedQuotaDomainScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = limitedQuotaCountByScopes(Quota.Scope.Domain);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class LimitedQuotaUserScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"User\":100}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = limitedQuotaCountByScopes(Quota.Scope.User);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }
    }

    @Nested
    class UnLimitedQuotaCount {
        private Quota<QuotaCountLimit, QuotaCountUsage> unLimitedQuotaCountByScopes(Quota.Scope scope) {
            return Quota.<QuotaCountLimit, QuotaCountUsage>builder()
                .used(QuotaCountUsage.count(12))
                .computedLimit(QuotaCountLimit.unlimited())
                .limitForScope(QuotaCountLimit.unlimited(), scope)
                .build();
        }

        @Nested
        class UnLimitedQuotaGlobalScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"Global\":null}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = unLimitedQuotaCountByScopes(Quota.Scope.Global);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class UnLimitedQuotaDomainScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"Domain\":null}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = unLimitedQuotaCountByScopes(Quota.Scope.Domain);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class UnLimitedQuotaUserScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"User\":null}}";
            private final Quota<QuotaCountLimit, QuotaCountUsage> quota = unLimitedQuotaCountByScopes(Quota.Scope.User);

            @Test
            void toJsonShouldSerializeQuotaCount() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaCountWrites().writes(DTOs.Quota$.MODULE$.toScala(
                    quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaCount() {
                assertThat(DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }
    }

    @Nested
    class UnknownQuotaScope {
        private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"Invalid\":100}}";

        @Test
        void fromJsonShouldThrowOnInvalidScope() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.quotaCReads().reads(Json.parse(json)).get().toJava())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void scopesShouldBeString() {
            assertThat(DTO_JSON_SERIALIZE.quotaScopeReads().reads(Json.parse("3")))
                .isInstanceOf(JsError.class);
        }
    }
}
