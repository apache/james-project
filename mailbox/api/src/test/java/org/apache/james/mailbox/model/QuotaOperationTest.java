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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaOperationTest {
    static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("user", Optional.empty());

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(QuotaOperation.class)
            .verify();
    }

    @Test
    void shouldNotThrowWhenCountIsZero() {
        assertThatCode(() -> new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(0), QuotaSizeUsage.size(5)))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenCountIsNegative() {
        assertThatThrownBy(() -> new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(-1), QuotaSizeUsage.size(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotThrowWhenSizeIsZero() {
        assertThatCode(() -> new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(5), QuotaSizeUsage.size(0)))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenSizeIsNegative() {
        assertThatThrownBy(() -> new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(5), QuotaSizeUsage.size(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
