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
package org.apache.james.quota.search.elasticsearch;

import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.QUOTA_RATIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import org.apache.james.core.Domain;
import org.apache.james.quota.search.QuotaBoundary;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaQueryConverterTest {
    QuotaQueryConverter testee;

    @BeforeEach
    void setup() {
        testee = new QuotaQueryConverter();
    }

    @Test
    void fromShouldReturnMatchAllWhenEmptyClauses() {
        QuotaQuery query = QuotaQuery.builder().build();
        QueryBuilder expected = matchAllQuery();

        QueryBuilder actual = testee.from(query);

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void fromShouldReturnDomainMatchWhenOnlyDomain() {
        QuotaQuery query = QuotaQuery.builder().hasDomain(Domain.of("my.tld")).build();
        QueryBuilder expected = termQuery(JsonMessageConstants.DOMAIN, "my.tld");

        QueryBuilder actual = testee.from(query);

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void fromShouldReturnQuotaRatioMatchWhenLessThan() {
        QuotaQuery query = QuotaQuery.builder().lessThan(new QuotaBoundary(0.1)).build();
        QueryBuilder expected = rangeQuery(QUOTA_RATIO).lte(0.1);

        QueryBuilder actual = testee.from(query);

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    void fromShouldReturnQuotaRatioMatchWhenMoreThan() {
        QuotaQuery query = QuotaQuery.builder().moreThan(new QuotaBoundary(0.1)).build();
        QueryBuilder expected = rangeQuery(QUOTA_RATIO).gte(0.1);

        QueryBuilder actual = testee.from(query);

        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

}
