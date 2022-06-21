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

import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.QUOTA_RATIO;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.quota.search.QuotaBoundary;
import org.apache.james.quota.search.QuotaQuery;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

class QuotaQueryConverterTest {
    QuotaQueryConverter testee;

    @BeforeEach
    void setup() {
        testee = new QuotaQueryConverter();
    }

    @Test
    void fromShouldReturnMatchAllWhenEmptyClauses() {
        QuotaQuery query = QuotaQuery.builder().build();
        Query expected = new MatchAllQuery.Builder().build()._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> assertThat(actual._kind()).isEqualTo(expected._kind()));
    }

    @Test
    void fromShouldReturnDomainMatchWhenOnlyDomain() {
        QuotaQuery query = QuotaQuery.builder().hasDomain(Domain.of("my.tld")).build();
        Query expected = new TermQuery.Builder()
            .field(DOMAIN)
            .value(new FieldValue.Builder().stringValue("my.tld").build())
            .build()
            ._toQuery();

        Query actual = testee.from(query);

        SoftAssertions.assertSoftly(softly -> {
            assertThat(actual._kind()).isEqualTo(expected._kind());
            assertThat(actual.term().field()).isEqualTo(expected.term().field());
            assertThat(actual.term().value().stringValue()).isEqualTo(expected.term().value().stringValue());
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
            assertThat(actual.range().field()).isEqualTo(expected.range().field());
            assertThat(actual.range().lte().to(Double.class)).isEqualTo(expected.range().lte().to(Double.class));
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
            assertThat(actual.range().field()).isEqualTo(expected.range().field());
            assertThat(actual.range().gte().to(Double.class)).isEqualTo(expected.range().gte().to(Double.class));
        });
    }

}
