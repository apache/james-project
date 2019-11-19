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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;

class GuavaUtilsTest {

    @Test
    void toMultimapShouldAcceptEmptyMaps() {
        assertThat(GuavaUtils.toMultimap(ImmutableMap
            .<String, List<String>>builder()
            .build())
            .asMap())
            .isEqualTo(ImmutableMap.of());
    }

    @Test
    void toMultimapShouldAcceptSingleValuesMaps() {
        assertThat(GuavaUtils.toMultimap(ImmutableMap
            .<String, List<String>>builder()
            .put("k1", ImmutableList.of("v1"))
            .put("k2", ImmutableList.of("v2"))
            .build())
            .asMap())
            .isEqualTo(ImmutableListMultimap.of(
                "k1", "v1",
                "k2", "v2")
            .asMap());
    }

    @Test
    void toMultimapShouldAcceptMultiplesValuesMaps() {
        assertThat(GuavaUtils.toMultimap(ImmutableMap
            .<String, List<String>>builder()
            .put("k1", ImmutableList.of("v1"))
            .put("k2", ImmutableList.of("v2", "v2.1"))
            .build())
            .asMap())
            .isEqualTo(ImmutableListMultimap.of(
                "k1", "v1",
                "k2", "v2",
                "k2", "v2.1")
                .asMap());
    }

    @Test
    void shouldStripEntriesWithEmptyList() {
        assertThat(GuavaUtils.toMultimap(ImmutableMap
            .<String, List<String>>builder()
            .put("k1", ImmutableList.of())
            .build())
            .asMap())
            .isEqualTo(ImmutableListMultimap.of().asMap());
    }
}
