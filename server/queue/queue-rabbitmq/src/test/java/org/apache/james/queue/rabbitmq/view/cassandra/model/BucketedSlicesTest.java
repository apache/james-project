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

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BucketedSlicesTest {
    
    private static final long ONE_HOUR_IN_SECONDS = 3600;

    private static final Instant FIRST_SLICE_INSTANT = Instant.parse("2018-05-20T12:00:00.000Z");
    private static final Instant FIRST_SLICE_INSTANT_NEXT_HOUR = FIRST_SLICE_INSTANT.plusSeconds(ONE_HOUR_IN_SECONDS);
    private static final Instant FIRST_SLICE_INSTANT_NEXT_TWO_HOUR = FIRST_SLICE_INSTANT.plusSeconds(ONE_HOUR_IN_SECONDS * 2);

    private static final Slice FIRST_SLICE = Slice.of(FIRST_SLICE_INSTANT, ONE_HOUR_IN_SECONDS);
    private static final Slice FIRST_SLICE_NEXT_TWO_HOUR = Slice.of(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR, ONE_HOUR_IN_SECONDS);

    @Nested
    class Validation {
    }

    @Test
    void allSlicesTillShouldReturnOnlyFirstSliceWhenEndAtInTheSameInterval() {
        assertThat(Slice.allSlicesTill(FIRST_SLICE, FIRST_SLICE_INSTANT.plusSeconds(3599)))
            .containsOnly(FIRST_SLICE);
    }

    @Test
    void allSlicesTillShouldReturnAllSlicesBetweenStartAndEndAt() {
        Stream<Slice> allSlices = Slice.allSlicesTill(FIRST_SLICE, FIRST_SLICE_INSTANT_NEXT_TWO_HOUR.plusSeconds(3599));

        assertThat(allSlices)
            .containsExactly(
                FIRST_SLICE,
                Slice.of(FIRST_SLICE_INSTANT_NEXT_HOUR, ONE_HOUR_IN_SECONDS),
                Slice.of(FIRST_SLICE_INSTANT_NEXT_TWO_HOUR, ONE_HOUR_IN_SECONDS));
    }

    @Test
    void allSlicesTillShouldReturnEmptyIfEndAtBeforeStartSlice() {
        Stream<Slice> allSlices = Slice.allSlicesTill(FIRST_SLICE_NEXT_TWO_HOUR, FIRST_SLICE_INSTANT);

        assertThat(allSlices).isEmpty();
    }
}