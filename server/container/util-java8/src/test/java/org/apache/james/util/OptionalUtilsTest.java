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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.steveash.guavate.Guavate;

public class OptionalUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void ifEmptyShouldPreserveValueOfEmptyOptionals() {
        Optional<Object> expected = OptionalUtils.peekOnEmpty(Optional.empty(), () -> { });

        assertThat(expected).isEmpty();
    }

    @Test
    public void ifEmptyShouldPreserveValueOfPresentOptionals() {
        String value = "value";
        Optional<String> expected = OptionalUtils.peekOnEmpty(Optional.of(value), () -> { });

        assertThat(expected).contains(value);
    }

    @Test
    public void ifEmptyShouldPerformOperationIfEmpty() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.peekOnEmpty(Optional.empty(), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(1);
    }

    @Test
    public void ifEmptyShouldNotPerformOperationIfPresent() {
        AtomicInteger operationCounter = new AtomicInteger(0);

        OptionalUtils.peekOnEmpty(Optional.of("value"), operationCounter::incrementAndGet);

        assertThat(operationCounter.get()).isEqualTo(0);
    }

    @Test
    public void toStreamShouldConvertEmptyOptionalToEmptyStream() {
        assertThat(
            OptionalUtils.toStream(Optional.empty())
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    public void toStreamShouldConvertFullOptionalToStream() {
        long value = 18L;
        assertThat(
            OptionalUtils.toStream(Optional.of(value))
                .collect(Guavate.toImmutableList()))
            .containsExactly(value);
    }
}
