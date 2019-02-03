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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

class FunctionalUtilsTest {

    @Nested
    class ToFunction {
        @Test
        void shouldCallConsumerAndReturnTheGivenParameter() {
            Counter counter = new Counter(26);
            Consumer<Integer> consumer = counter::increment;
            Function<Integer, Integer> function = FunctionalUtils.toFunction(consumer);

            assertThat(function.apply(16)).isEqualTo(16);
            assertThat(counter.getCounter()).isEqualTo(42);
        }

        private class Counter {
            private Integer counter;

            public Counter(Integer counter) {
                this.counter = counter;
            }

            public void increment(Integer other) {
                counter += other;
            }

            public Integer getCounter() {
                return counter;
            }
        }
    }

    @Nested
    class ToPredicate {
        @Test
        void shouldKeepProperty() {
            Function<Integer, Boolean> function = value -> value % 42 == 0;
            Predicate<Integer> predicate = FunctionalUtils.toPredicate(function);

            assertThat(predicate.test(5)).isFalse();
            assertThat(predicate.test(42)).isTrue();
        }
    }
}