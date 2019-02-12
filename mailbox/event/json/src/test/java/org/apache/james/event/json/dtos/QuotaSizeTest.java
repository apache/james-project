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

import org.apache.james.core.quota.QuotaSize;
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

class QuotaSizeTest {
    @Test
    void quotaSizeShouldBeWellSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaValueWrites().writes(QuotaSize.size(18)))
            .isEqualTo(new JsNumber(BigDecimal.valueOf(18)));
    }

    @Test
    void quotaSizeShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.quotaSizeReads().reads(new JsNumber(BigDecimal.valueOf(18))).get())
            .isEqualTo(QuotaSize.size(18));
    }

    @Test
    void quotaSizeShouldBeWellSerializedWhenUnlimited() {
        assertThat(DTO_JSON_SERIALIZE.quotaValueWrites().writes(QuotaSize.unlimited()))
            .isEqualTo(JsNull$.MODULE$);
    }

    @Test
    void quotaSizeShouldBeWellDeSerializedWhenUnlimited() {
        assertThat(DTO_JSON_SERIALIZE.quotaSizeReads().reads(JsNull$.MODULE$).get())
            .isEqualTo(QuotaSize.unlimited());
    }

    @Test
    void quotaSizeShouldReturnErrorWhenString() {
        assertThat(DTO_JSON_SERIALIZE.quotaSizeReads().reads(new JsString("18")))
            .isInstanceOf(JsError.class);
    }

    @Nested
    class LimitedQuotaSize {
        private Quota<QuotaSize> limitedQuotaSizeByScopes(Quota.Scope scope) {
            return Quota.<QuotaSize>builder()
                .used(QuotaSize.size(12))
                .computedLimit(QuotaSize.size(100))
                .limitForScope(QuotaSize.size(100), scope)
                .build();
        }

        @Nested
        class LimitedQuotaGlobalScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"Global\":100}}";
            private final Quota<QuotaSize> quota = limitedQuotaSizeByScopes(Quota.Scope.Global);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class LimitedQuotaDomainScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"Domain\":100}}";
            private final Quota<QuotaSize> quota = limitedQuotaSizeByScopes(Quota.Scope.Domain);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class LimitedQuotaUserScope {
            private final String json = "{\"used\":12,\"limit\":100,\"limits\":{\"User\":100}}";
            private final Quota<QuotaSize> quota = limitedQuotaSizeByScopes(Quota.Scope.User);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }
    }

    @Nested
    class UnLimitedQuotaSize {
        private Quota<QuotaSize> unLimitedQuotaSizeByScopes(Quota.Scope scope) {
            return Quota.<QuotaSize>builder()
                .used(QuotaSize.size(12))
                .computedLimit(QuotaSize.unlimited())
                .limitForScope(QuotaSize.unlimited(), scope)
                .build();
        }

        @Nested
        class UnLimitedQuotaGlobalScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"Global\":null}}";
            private final Quota<QuotaSize> quota = unLimitedQuotaSizeByScopes(Quota.Scope.Global);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class UnLimitedQuotaDomainScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"Domain\":null}}";
            private final Quota<QuotaSize> quota = unLimitedQuotaSizeByScopes(Quota.Scope.Domain);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }

        @Nested
        class UnLimitedQuotaUserScope {
            private final String json = "{\"used\":12,\"limit\":null,\"limits\":{\"User\":null}}";
            private final Quota<QuotaSize> quota = unLimitedQuotaSizeByScopes(Quota.Scope.User);

            @Test
            void toJsonShouldSerializeQuotaSize() {
                assertThatJson(DTO_JSON_SERIALIZE.quotaSizeWrites().writes(DTOs.Quota$.MODULE$.toScala(quota)).toString())
                    .isEqualTo(json);
            }

            @Test
            void fromJsonShouldDeserializeQuotaSize() {
                assertThat(DTO_JSON_SERIALIZE.quotaSReads().reads(Json.parse(json)).get().toJava())
                    .isEqualTo(quota);
            }
        }
    }
}
