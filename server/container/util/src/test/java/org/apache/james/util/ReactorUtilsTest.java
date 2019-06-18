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

import reactor.core.publisher.Mono;

class ReactorUtilsTest {

    @Nested
    class ExecuteAndEmpty {
        @Test
        void shouldExecuteTheRunnableAndReturnEmpty() {
            Counter counter = new Counter(1);

            Mono<?> reactor = Mono.empty()
                    .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> counter.increment(2)))
                    .map(FunctionalUtils.toFunction(any -> counter.increment(4)));

            assertThat(reactor.hasElement().block()).isFalse();
            assertThat(counter.getCounter()).isEqualTo(3);
        }

        @Test
        void shouldNotExecuteTheRunnableAndReturnTheValue() {
            Counter counter = new Counter(1);

            Mono<?> reactor = Mono.just(42)
                    .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> counter.increment(2)))
                    .map(FunctionalUtils.toFunction(any -> counter.increment(4)));

            assertThat(reactor.hasElement().block()).isTrue();
            assertThat(counter.getCounter()).isEqualTo(5);
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
}