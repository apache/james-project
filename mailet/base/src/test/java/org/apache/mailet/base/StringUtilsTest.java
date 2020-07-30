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
package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class StringUtilsTest {

    @Test
    void listToStringShouldThrowWhenListIsNull() {
        assertThatThrownBy(() -> StringUtils.listToString(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void listToStringShouldReturnOnlyBracketsWhenListIsEmpty() {
        String listToString = StringUtils.listToString(ImmutableList.<String>of());

        assertThat(listToString).isEqualTo("[]");
    }

    @Test
    void listToStringShouldReturnOneElementWhenListContainsOneElement() {
        String listToString = StringUtils.listToString(ImmutableList.of("first"));

        assertThat(listToString).isEqualTo("[first]");
    }

    @Test
    void listToStringShouldReturnSeparatedElementsWhenListContainsMultipleElements() {
        String listToString = StringUtils.listToString(ImmutableList.of("first", "second", "fourth"));

        assertThat(listToString).isEqualTo("[first, second, fourth]");
    }

    @Test
    void listToStringShouldThrowWhenListContainsANullElement() {
        assertThatThrownBy(() -> StringUtils.listToString(ImmutableList.of("first", null, "fourth")))
            .isInstanceOf(NullPointerException.class);
    }
}
