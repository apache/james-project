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
package org.apache.james.core.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public interface QuotaUsageValueTest<T extends QuotaLimitValue<T>, U extends QuotaUsageValue<U, T>> {

    U usageInstance(long value);

    T limitInstance(long value);

    T unlimited();

    @Test
    default void greaterThanShouldReturnFalseWhenFirstEqualToSecond() {
        assertThat(usageInstance(1).greaterThan(usageInstance(1))).isFalse();
    }

    @Test
    default void greaterThanShouldReturnFalseWhenFirstSmallerThanSecond() {
        assertThat(usageInstance(1).greaterThan(usageInstance(2))).isFalse();
    }

    @Test
    default void greaterThanShouldReturnTrueWhenFirstGreaterThanSecond() {
        assertThat(usageInstance(2).greaterThan(usageInstance(1))).isTrue();
    }

    @Test
    default void greaterThanShouldReturnFalseWhenUsageEqualToLimit() {
        assertThat(usageInstance(1).exceedLimit(limitInstance(1))).isFalse();
    }

    @Test
    default void greaterThanShouldReturnFalseWhenUsageSmallerThanLimit() {
        assertThat(usageInstance(1).exceedLimit(limitInstance(2))).isFalse();
    }

    @Test
    default void greaterThanShouldReturnTrueWhenUsageGreaterThanLimit() {
        assertThat(usageInstance(2).exceedLimit(limitInstance(1))).isTrue();
    }

    @Test
    default void greaterThanShouldReturnFalseWhenUsageIsLimitedAndLimitIsUnlimited() {
        assertThat(usageInstance(1).exceedLimit(unlimited())).isFalse();
    }

    @Test
    default void addShouldReturnSumResult() {
        assertThat(usageInstance(12).add(usageInstance(23))).isEqualTo(usageInstance(35));
    }
}
