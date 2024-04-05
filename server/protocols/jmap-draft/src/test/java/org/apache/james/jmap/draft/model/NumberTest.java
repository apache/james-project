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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.apache.james.jmap.model.Number;

public class NumberTest {
    @Test
    public void fromOutboundLongShouldReturnMinValueWhenNegativeValue() throws Exception {
        assertThat(Number.BOUND_SANITIZING_FACTORY.from(-1))
            .isEqualTo(Number.ZERO);
    }

    @Test
    public void fromOutboundLongShouldSanitizeTooBigNumbers() throws Exception {
        assertThat(Number.BOUND_SANITIZING_FACTORY.from(Number.MAX_VALUE  + 1))
            .isEqualTo(Number.fromLong(Number.MAX_VALUE));
    }

    @Test
    public void fromLongShouldThrowWhenNegativeValue() throws Exception {
        assertThatThrownBy(() ->
            Number.fromLong(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLongShouldThrowWhenOver2Pow53Value() throws Exception {
        assertThatThrownBy(() ->
            Number.fromLong(Number.MAX_VALUE + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromLongShouldReturnValueWhenZero() throws Exception {
        assertThat(Number.fromLong(0).asLong())
            .isEqualTo(0);
    }

    @Test
    public void fromLongShouldReturnValueWhenMaxValue() throws Exception {
        assertThat(Number.fromLong(Number.MAX_VALUE).asLong())
            .isEqualTo(Number.MAX_VALUE);
    }

}