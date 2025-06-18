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

package org.apache.james.quota.search;

import static org.apache.james.quota.search.QuotaBoundaryFixture._50;
import static org.apache.james.quota.search.QuotaBoundaryFixture._75;

import org.apache.james.core.Domain;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaQueryTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaQuery.class)
            .withPrefabValues(
                ImmutableList.class,
                ImmutableList.of(QuotaClause.moreThan(_50), QuotaClause.moreThan(_75)),
                ImmutableList.of(QuotaClause.moreThan(new QuotaBoundary(0.4)), QuotaClause.lessThan(new QuotaBoundary(0.9))))
            .verify();
    }

    @Test
    public void builderShouldProvideDefaults() {
        QuotaQuery quotaQuery = QuotaQuery.builder()
            .build();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaQuery.getClause()).isEqualTo(QuotaClause.and());
        softly.assertThat(quotaQuery.getLimit()).isEqualTo(Limit.unlimited());
        softly.assertThat(quotaQuery.getOffset()).isEqualTo(Offset.none());
        softly.assertAll();
    }

    @Test
    public void builderShouldUseProvidedValues() {
        Limit limit = Limit.of(10);

        Offset offset = Offset.of(50);
        QuotaQuery quotaQuery = QuotaQuery.builder()
            .withLimit(limit)
            .withOffset(offset)
            .lessThan(_75)
            .moreThan(_50)
            .hasDomain(Domain.LOCALHOST)
            .build();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(quotaQuery.getClause())
            .isEqualTo(QuotaClause.and(
                QuotaClause.lessThan(_75),
                QuotaClause.moreThan(_50),
                QuotaClause.hasDomain(Domain.LOCALHOST)));
        softly.assertThat(quotaQuery.getLimit()).isEqualTo(limit);
        softly.assertThat(quotaQuery.getOffset()).isEqualTo(offset);
        softly.assertAll();
    }

}
