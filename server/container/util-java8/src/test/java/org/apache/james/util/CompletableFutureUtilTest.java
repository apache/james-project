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

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class CompletableFutureUtilTest {

    @Test
    public void allOfShouldUnboxEmptyStream() {
        assertThat(
            CompletableFutureUtil.allOf(Stream.empty())
                .join()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void allOfShouldUnboxStream() {
        long value1 = 18L;
        long value2 = 19L;
        long value3 = 20L;
        assertThat(
            CompletableFutureUtil.allOf(
                Stream.of(
                    CompletableFuture.completedFuture(value1),
                    CompletableFuture.completedFuture(value2),
                    CompletableFuture.completedFuture(value3)))
                .join()
                .collect(Guavate.toImmutableList()))
            .containsOnly(value1, value2, value3);
    }
}
