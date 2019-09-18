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

import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class HeaderTest {

    @Test
    public void builderShouldThrowWhenHeaderIsNull() {
        assertThatThrownBy(() -> Header.builder().header(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenHeaderIsEmpty() {
        assertThatThrownBy(() -> Header.builder().header(ImmutableList.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void builderShouldThrowWhenHeaderHasMoreThanTwoElements() {
        assertThatThrownBy(() -> Header.builder().header(ImmutableList.of("1", "2", "3"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void buildShouldThrowWhenNameIsNotGiven() {
        assertThatThrownBy(() -> Header.builder().build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldSetNameWhenGiven() {
        String expectedName = "name";
        Header header = Header.builder().header(ImmutableList.of(expectedName)).build();

        assertThat(header.getName()).isEqualTo(expectedName);
        assertThat(header.getValue()).isEmpty();
    }

    @Test
    public void buildShouldSetValueWhenGiven() {
        String expectedName = "name";
        String expectedValue = "value";
        Header header = Header.builder().header(ImmutableList.of(expectedName, expectedValue)).build();

        assertThat(header.getName()).isEqualTo(expectedName);
        assertThat(header.getValue()).isEqualTo(Optional.of(expectedValue));
    }
}
