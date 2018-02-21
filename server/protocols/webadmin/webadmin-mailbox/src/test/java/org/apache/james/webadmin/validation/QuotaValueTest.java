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

package org.apache.james.webadmin.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import spark.HaltException;

public class QuotaValueTest {

    @Test
    public void quotaCountShouldThrowWhenNotANumber() {
        assertThatThrownBy(() -> QuotaValue.quotaCount("invalid"))
            .isInstanceOf(HaltException.class);
    }

    @Test
    public void quotaCountShouldParseZero() {
        assertThat(QuotaValue.quotaCount("0").asLong())
            .isEqualTo(0);
    }

    @Test
    public void quotaCountShouldParsePositiveValue() {
        assertThat(QuotaValue.quotaCount("42").asLong())
            .isEqualTo(42);
    }

    @Test
    public void quotaCountShouldThrowOnNegativeNumber() {
        assertThatThrownBy(() -> QuotaValue.quotaCount("-1"))
            .isInstanceOf(HaltException.class);
    }

    @Test
    public void quotaSizeShouldThrowWhenNotANumber() {
        assertThatThrownBy(() -> QuotaValue.quotaSize("invalid"))
            .isInstanceOf(HaltException.class);
    }

    @Test
    public void quotaSizeShouldParseZero() {
        assertThat(QuotaValue.quotaSize("0").asLong())
            .isEqualTo(0);
    }

    @Test
    public void quotaSizeShouldParsePositiveValue() {
        assertThat(QuotaValue.quotaSize("42").asLong())
            .isEqualTo(42);
    }

    @Test
    public void quotaSizeShouldThrowOnNegativeNumber() {
        assertThatThrownBy(() -> QuotaValue.quotaSize("-1"))
            .isInstanceOf(HaltException.class);
    }

}
