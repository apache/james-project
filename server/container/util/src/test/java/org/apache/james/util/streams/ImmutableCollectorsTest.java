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

package org.apache.james.util.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

class ImmutableCollectorsTest {

    @Test
    void immutableListCollectorShouldReturnEmptyImmutableListWhenEmptyStream() {
        String[] data = {};
        List<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableList());
        assertThat(actual).isInstanceOf(ImmutableList.class);
        assertThat(actual).isEmpty();
    }

    @Test
    void immutableListCollectorShouldReturnImmutableListWhenOneElementStream() {
        String[] data = {"a"};
        List<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableList());
        assertThat(actual).isInstanceOf(ImmutableList.class);
        assertThat(actual).containsExactly("a");
    }

    @Test
    void immutableListCollectorShouldReturnImmutableListWhen3ElementsStream() {
        String[] data = {"a", "b", "c"};
        List<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableList());
        assertThat(actual).isInstanceOf(ImmutableList.class);
        assertThat(actual).containsExactly("a", "b", "c");
    }

    @Test
    void immutableSetCollectorShouldReturnEmptyImmutableSetWhenEmptyStream() {
        String[] data = {};
        Set<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableSet());
        assertThat(actual).isInstanceOf(ImmutableSet.class);
        assertThat(actual).isEmpty();
    }

    @Test
    void immutableSetCollectorShouldReturnImmutableSetWhenOneElementStream() {
        String[] data = {"a"};
        Set<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableSet());
        assertThat(actual).isInstanceOf(ImmutableSet.class);
        assertThat(actual).containsExactly("a");
    }

    @Test
    void immutableSetCollectorShouldReturnImmutableSetWhen3ElementsStream() {
        String[] data = {"a", "b", "c"};
        Set<String> actual = Arrays.stream(data)
            .collect(Guavate.toImmutableSet());
        assertThat(actual).isInstanceOf(ImmutableSet.class);
        assertThat(actual).containsExactly("a", "b", "c");
    }


    @Test
    void immutableMapCollectorShouldReturnEmptyImmutableMapWhenEmptyStream() {
        String[] data = {};
        Map<String, Integer> actual = Arrays.stream(data)
                .collect(Guavate.toImmutableMap(x -> x.toUpperCase(Locale.US), String::length));
        assertThat(actual).isInstanceOf(ImmutableMap.class);
        assertThat(actual).isEmpty();
    }

    @Test
    void immutableMapCollectorShouldReturnAppliedImmutableMapWhenOneElementStream() {
        String[] data = {"a"};
        Map<String, Integer> actual = Arrays.stream(data)
                .collect(Guavate.toImmutableMap(x -> x.toUpperCase(Locale.US), String::length));
        assertThat(actual).isInstanceOf(ImmutableMap.class);
        assertThat(actual).containsExactly(entry("A", 1));
    }

    @Test
    void immutableMapCollectorShouldReturnAppliedImmutableMapWhen3ElementsStream() {
        String[] data = {"a", "bb", "ccc"};
        Map<String, Integer> actual = Arrays.stream(data)
                .collect(Guavate.toImmutableMap(x -> x.toUpperCase(Locale.US), String::length));
        assertThat(actual).isInstanceOf(ImmutableMap.class);
        assertThat(actual).containsExactly(entry("A", 1), entry("BB", 2), entry("CCC", 3));
    }

}

