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

import java.util.stream.Stream;

import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.github.steveash.guavate.Guavate;

public class StreamUtilsTest {

    @Test
    public void flattenShouldReturnEmptyWhenEmptyStreams() {
        assertThat(
            StreamUtils.<Integer>flatten(ImmutableList.of())
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void flattenShouldPreserveSingleStreams() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2, 3)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

    @Test
    public void flattenShouldMergeSeveralStreamsTogether() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2, 3),
                Stream.of(4, 5)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    public void flattenShouldAcceptEmptyStreams() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of()))
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void flattenShouldMergeEmptyStreamsWithOtherData() {
        assertThat(
            StreamUtils.flatten(ImmutableList.of(
                Stream.of(1, 2),
                Stream.of(),
                Stream.of(3)))
                .collect(Guavate.toImmutableList()))
            .containsExactly(1, 2, 3);
    }

}