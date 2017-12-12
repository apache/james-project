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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class NumberTest {
    @Test
    public void fromIntShouldReturnMinValueWhenNegativeValueWithLenient() throws Exception {
        assertThat(Number.fromOutboundLong(-1))
            .isEqualTo(Number.ZERO);
    }

    @Test
    public void fromLongShouldReturnMinValueWhenNegativeValueWithLenient() throws Exception {
        assertThat(Number.fromOutboundLong(-1))
            .isEqualTo(Number.ZERO);
    }

    @Test
    public void fromIntShouldThrowWhenNegativeValueWithStrict() throws Exception {
        assertThatThrownBy(() ->
            Number.fromInt(-1))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void fromIntShouldReturnIntegerValue() throws Exception {
        assertThat(Number.fromInt(1).asLong())
            .isEqualTo(1);
    }

    @Test
    public void fromLongShouldThrowWhenNegativeValue() throws Exception {
        assertThatThrownBy(() ->
            Number.fromLong(-1))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void fromLongShouldThrowWhenOver2Pow53Value() throws Exception {
        assertThatThrownBy(() ->
            Number.fromLong(Number.MAX_VALUE + 1))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void fromLongShouldReturnLongValue() throws Exception {
        assertThat(Number.fromLong(1).asLong())
            .isEqualTo(1);
    }

    @Test
    public void ensureLessThanShouldThrowWhenOverSpecifiedValue() throws Exception {
        assertThatThrownBy(() ->
            Number.fromInt(11).ensureLessThan(10))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void ensureLessThanShouldReturnNumberWhenEqualValue() throws Exception {
        Number number = Number.fromInt(10);
        assertThat(number.ensureLessThan(10))
            .isEqualTo(number);
    }

    @Test
    public void ensureLessThanShouldReturnNumberWhenLessThanMaxValue() throws Exception {
        Number number = Number.fromInt(10);
        assertThat(number.ensureLessThan(11))
            .isEqualTo(number);
    }
}