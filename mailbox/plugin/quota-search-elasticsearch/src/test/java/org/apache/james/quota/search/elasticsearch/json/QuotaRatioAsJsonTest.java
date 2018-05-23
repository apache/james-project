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
package org.apache.james.quota.search.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaRatioAsJsonTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaRatioAsJson.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void buildShouldThrownWhenUserIsNull() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldThrownWhenUserIsEmpty() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .user("")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldThrownWhenQuotaRatioIsNull() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .user("user")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getDomainShouldReturnEmptyWhenNone() {
        QuotaRatioAsJson quotaRatioAsJson = QuotaRatioAsJson.builder()
            .user("user")
            .quotaRatio(0.3)
            .build();

        assertThat(quotaRatioAsJson.getDomain()).isEmpty();
    }

    @Test
    public void getDomainShouldReturnTheDomainWhenGiven() {
        String domain = "domain";
        QuotaRatioAsJson quotaRatioAsJson = QuotaRatioAsJson.builder()
            .user("user")
            .domain(Optional.of(domain))
            .quotaRatio(0.2)
            .build();

        assertThat(quotaRatioAsJson.getDomain()).contains(domain);
    }
}
