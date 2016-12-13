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

package org.apache.james.mailbox.cassandra.mail.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

public class MapMergerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void mergeShouldReturnMapWhenMapsMatch() {
        Map<Integer, String> lhs = ImmutableMap.of(1, "a");
        Map<Integer, Integer> rhs = ImmutableMap.of(1, 2);
        Map<Integer, String> actual = MapMerger.merge(lhs, rhs, Strings::repeat);
        assertThat(actual).hasSize(1).containsEntry(1, "aa");
    }

    @Test
    public void mergeShouldReturnEmptyMapWhenMapsDontMatch() {
        Map<Integer, String> lhs = ImmutableMap.of(1, "a");
        Map<Integer, Integer> rhs = ImmutableMap.of(3, 2);
        Map<Integer, String> actual = MapMerger.merge(lhs, rhs, Strings::repeat);
        assertThat(actual).hasSize(0);
    }

    @Test
    public void mergeShouldReturnEmptyMapWhenFirstMapIsEmpty() {
        Map<Integer, String> lhs = ImmutableMap.of();
        Map<Integer, Integer> rhs = ImmutableMap.of(3, 2);
        Map<Integer, String> actual = MapMerger.merge(lhs, rhs, Strings::repeat);
        assertThat(actual).hasSize(0);
    }

    @Test
    public void mergeShouldThrowWhenFirstMapIsNull() {
        expectedException.expect(NullPointerException.class);
        Map<Integer, String> lhs = null;
        Map<Integer, Integer> rhs = ImmutableMap.of(3, 2);
        Map<Integer, String> actual = MapMerger.merge(lhs, rhs, Strings::repeat);
        assertThat(actual).hasSize(0);
    }

    @Test
    public void mergeThrowWhenSecondMapIsNull() {
        Map<Integer, String> lhs = ImmutableMap.of(1, "b");
        Map<Integer, Integer> rhs = null;
        expectedException.expect(NullPointerException.class);
        MapMerger.merge(lhs, rhs, Strings::repeat);
    }
}
