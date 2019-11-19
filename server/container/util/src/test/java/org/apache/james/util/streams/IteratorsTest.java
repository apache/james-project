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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

import reactor.core.publisher.Flux;

class IteratorsTest {

    @Test
    void toStreamShouldReturnEmptyStreamWhenEmptyIterator() {
        //Given
        UnmodifiableIterator<String> emptyIterator = ImmutableList.<String>of().iterator();

        //When
        Stream<String> actual = Iterators.toStream(emptyIterator);

        //Then
        assertThat(actual.count()).isEqualTo(0);
    }

    @Test
    void toStreamShouldReturnSameContent() {
        //Given
        UnmodifiableIterator<String> iterator = ImmutableList.of("a", "b", "c").iterator();

        //When
        Stream<String> actual = Iterators.toStream(iterator);

        //Then
        assertThat(actual.collect(toList())).containsExactly("a", "b", "c");
    }

    @Test
    void toFluxShouldReturnEmptyStreamWhenEmptyIterator() {
        UnmodifiableIterator<String> emptyIterator = ImmutableList.<String>of().iterator();

        Flux<String> actual = Iterators.toFlux(emptyIterator);

        assertThat(actual.count().block()).isEqualTo(0);
    }

    @Test
    void toFluxShouldReturnSameContent() {
        UnmodifiableIterator<String> iterator = ImmutableList.of("a", "b", "c").iterator();

        Flux<String> actual = Iterators.toFlux(iterator);

        assertThat(actual.collect(toList()).block()).containsExactly("a", "b", "c");
    }

}
