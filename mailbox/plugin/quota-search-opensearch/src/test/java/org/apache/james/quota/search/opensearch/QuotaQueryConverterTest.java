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
package org.apache.james.quota.search.opensearch;

import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.QUOTA_RATIO;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.quota.search.QuotaBoundary;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;

class QuotaQueryConverterTest {
    org.apache.james.quota.search.elasticsearch.v8.QuotaQueryConverter testee;

    @BeforeEach
    void setup() {
        testee = new org.apache.james.quota.search.elasticsearch.v8.QuotaQueryConverter();
    }

    @Test
    void fromShouldReturnMatchAllWhenEmptyClauses() {
        QuotaQuery query = QuotaQuery.builder().build();
        Query expected = new MatchAllQuery.Builder().build()._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> {
            assertThat(actual._kind()).isEqualTo(expected._kind());
            assertThat(actual._get().toString()).isEqualTo(expected._get().toString());
        });
    }

    @Test
    void fromShouldReturnDomainMatchWhenOnlyDomain() {
        QuotaQuery query = QuotaQuery.builder().hasDomain(Domain.of("my.tld")).build();
        Query expected = new TermQuery.Builder()
            .field(JsonMessageConstants.DOMAIN)
            .value("my.tld")
            .build()
            ._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> {
            assertThat(actual._kind()).isEqualTo(expected._kind());
            assertThat(actual._get().toString()).isEqualTo(expected._get().toString());
        });
    }

    @Test
    void fromShouldReturnQuotaRatioMatchWhenLessThan() {
        QuotaQuery query = QuotaQuery.builder().lessThan(new QuotaBoundary(0.1)).build();
        Query expected = new RangeQuery.Builder()
            .field(QUOTA_RATIO)
            .lte(JsonData.of(0.1))
            .build()
            ._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> {
            assertThat(actual._kind()).isEqualTo(expected._kind());
            assertThat(actual._get().toString()).isEqualTo(expected._get().toString());
        });
    }

    @Test
    void fromShouldReturnQuotaRatioMatchWhenMoreThan() {
        QuotaQuery query = QuotaQuery.builder().moreThan(new QuotaBoundary(0.1)).build();
        Query expected = new RangeQuery.Builder()
            .field(QUOTA_RATIO)
            .gte(JsonData.of(0.1))
            .build()
            ._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> {
            assertThat(actual._kind()).isEqualTo(expected._kind());
            assertThat(actual._get().toString()).isEqualTo(expected._get().toString());
        });
    }

}
