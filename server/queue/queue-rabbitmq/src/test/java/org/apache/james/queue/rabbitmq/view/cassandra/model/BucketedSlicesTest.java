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

package org.apache.james.queue.rabbitmq.view.cassandra.model;

import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BucketedSlicesTest {
    
    private static final long ONE_HOUR_IN_SECONDS = 3600;

    private static final Instant FIRST_SLICE_INSTANT = Instant.parse("2018-05-20T12:00:00.000Z");
    private static final Instant FIRST_SLICE_INSTANT_NEXT_HOUR = FIRST_SLICE_INSTANT.plusSeconds(ONE_HOUR_IN_SECONDS);
    private static final Instant FIRST_SLICE_INSTANT_NEXT_TWO_HOUR = FIRST_SLICE_INSTANT.plusSeconds(ONE_HOUR_IN_SECONDS * 2);

    private static final Duration ONE_HOUR_SLICE_WINDOW = Duration.ofSeconds(ONE_HOUR_IN_SECONDS);
    private static final Slice FIRST_SLICE = Slice.of(FIRST_SLICE_INSTANT);
    private static final Slice FIRST_SLICE_NEXT_TWO_HOUR = Slice.of(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR);

    @Test
    void bucketIdShouldMatchBeanContract() {
        EqualsVerifier.forClass(BucketId.class)
            .verify();
    }

    @Test
    void sliceShouldMatchBeanContract() {
        EqualsVerifier.forClass(Slice.class)
            .verify();
    }

    @Test
    void bucketIdShouldThrowWhenValueIsNegative() {
        assertThatThrownBy(() -> BucketId.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allSlicesTillShouldReturnOnlyFirstSliceWhenEndAtInTheSameInterval() {
        assertThat(FIRST_SLICE.allSlicesTill(FIRST_SLICE_INSTANT.plusSeconds(ONE_HOUR_IN_SECONDS - 1), ONE_HOUR_SLICE_WINDOW))
            .containsOnly(FIRST_SLICE);
    }

    @Test
    void allSlicesTillShouldReturnAllSlicesBetweenStartAndEndAt() {
        Stream<Slice> allSlices = FIRST_SLICE.allSlicesTill(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR.plusSeconds(ONE_HOUR_IN_SECONDS - 1), ONE_HOUR_SLICE_WINDOW);

        assertThat(allSlices)
            .containsExactly(
                FIRST_SLICE,
                Slice.of(FIRST_SLICE_INSTANT_NEXT_HOUR),
                Slice.of(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR));
    }

    @Test
    void allSlicesTillShouldReturnSameSlicesWhenEndAtsAreInTheSameInterval() {
        Stream<Slice> allSlicesEndAtTheStartOfWindow = FIRST_SLICE.allSlicesTill(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR, ONE_HOUR_SLICE_WINDOW);
        Stream<Slice> allSlicesEndAtTheMiddleOfWindow = FIRST_SLICE.allSlicesTill(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR.plusSeconds(1000), ONE_HOUR_SLICE_WINDOW);
        Stream<Slice> allSlicesEndAtTheEndWindow = FIRST_SLICE.allSlicesTill(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR.plusSeconds(ONE_HOUR_IN_SECONDS - 1), ONE_HOUR_SLICE_WINDOW);

        Slice [] allSlicesInThreeHours = {
            FIRST_SLICE,
            Slice.of(FIRST_SLICE_INSTANT_NEXT_HOUR),
            Slice.of(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR)};

        assertThat(allSlicesEndAtTheStartOfWindow)
            .containsExactly(allSlicesInThreeHours);

        assertThat(allSlicesEndAtTheMiddleOfWindow)
            .containsExactly(allSlicesInThreeHours);

        assertThat(allSlicesEndAtTheEndWindow)
            .containsExactly(allSlicesInThreeHours);
    }

    @Test
    void allSlicesTillShouldReturnEmptyIfEndAtBeforeStartSlice() {
        Stream<Slice> allSlices = FIRST_SLICE_NEXT_TWO_HOUR.allSlicesTill(FIRST_SLICE_INSTANT, ONE_HOUR_SLICE_WINDOW);

        assertThat(allSlices).isEmpty();
    }
}