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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaClauseTest {
    
    @Test
    public void lessThanShouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaClause.LessThan.class)
            .allFieldsShouldBeUsed()
            .verify();
    }
    
    @Test
    public void moreThanShouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaClause.MoreThan.class)
            .allFieldsShouldBeUsed()
            .verify();
    }
    
    @Test
    public void hasDomainShouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaClause.HasDomain.class)
            .allFieldsShouldBeUsed()
            .verify();
    }
    
    @Test
    public void andShouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaClause.And.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void lessThanClauseShouldThrowWhenQuotaThresholdIsNull() {
        assertThatThrownBy(() -> QuotaClause.lessThan(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void moreThanClauseShouldThrowWhenQuotaThresholdIsNull() {
        assertThatThrownBy(() -> QuotaClause.moreThan(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void hasDomainClauseShouldThrowWhenDomainIsNull() {
        assertThatThrownBy(() -> QuotaClause.hasDomain(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void andClauseShouldThrowWhenClauseIsNull() {
        List<QuotaClause> clauses = null;
        assertThatThrownBy(() -> QuotaClause.and(clauses))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void lessThanClauseShouldInstanciate() {
        QuotaClause.LessThan lessThanQuery = QuotaClause.lessThan(_50);

        assertThat(lessThanQuery.getQuotaBoundary()).isEqualTo(_50);
    }

    @Test
    public void moreThanClauseShouldInstanciate() {
        QuotaClause.MoreThan moreThanQuery = QuotaClause.moreThan(_50);

        assertThat(moreThanQuery.getQuotaBoundary()).isEqualTo(_50);
    }

    @Test
    public void hasDomainClauseShouldInstanciate() {
        Domain domain = Domain.of("domain.org");

        QuotaClause.HasDomain hasDomainQuery = QuotaClause.hasDomain(domain);

        assertThat(hasDomainQuery.getDomain()).isEqualTo(domain);
    }

    @Test
    public void andClauseShouldInstanciate() {
        QuotaClause.MoreThan first = QuotaClause.moreThan(_50);
        QuotaClause.MoreThan second = QuotaClause.moreThan(_75);

        QuotaClause.And andQuery = QuotaClause.and(first, second);

        assertThat(andQuery.getClauses())
            .containsExactly(first, second);
    }

    @Test
    public void nestedAndClausesAreNotSupported() {
        QuotaClause.MoreThan first = QuotaClause.moreThan(_50);
        QuotaClause.MoreThan second = QuotaClause.moreThan(_75);

        assertThatThrownBy(() -> QuotaClause.and(first, QuotaClause.and(second)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
