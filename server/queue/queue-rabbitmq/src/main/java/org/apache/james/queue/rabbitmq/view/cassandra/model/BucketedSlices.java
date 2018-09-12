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

import java.time.Instant;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;

public class BucketedSlices {

    public static class BucketId {

        public static BucketId of(int bucketId) {
            return new BucketId(bucketId);
        }

        private final int value;

        private BucketId(int value) {
            Preconditions.checkArgument(value >= 0, "sliceWindowSizeInSecond should not be negative");

            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof BucketId) {
                BucketId bucketId = (BucketId) o;

                return Objects.equals(this.value, bucketId.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }
    }

    public static class Slice {

        public static Slice of(Instant sliceStartInstant, long sliceWindowSizeInSecond) {
            return new Slice(sliceStartInstant, sliceWindowSizeInSecond);
        }

        public static Stream<Slice> allSlicesTill(Slice firstSlice, Instant endAt) {
            long sliceCount = calculateSliceCount(firstSlice, endAt);
            long startAtSeconds =  firstSlice.getStartSliceInstant().getEpochSecond();
            long sliceWindowSizeInSecond = firstSlice.getSliceWindowSizeInSecond();

            return LongStream.range(0, sliceCount)
                .map(slicePosition -> startAtSeconds + sliceWindowSizeInSecond * slicePosition)
                .mapToObj(Instant::ofEpochSecond)
                .map(sliceStartInstant -> Slice.of(sliceStartInstant, firstSlice.getSliceWindowSizeInSecond()));
        }

        private static long calculateSliceCount(Slice firstSlice, Instant endAt) {
            long startAtSeconds =  firstSlice.getStartSliceInstant().getEpochSecond();
            long endAtSeconds = endAt.getEpochSecond();
            long timeDiffInSecond = endAtSeconds - startAtSeconds;

            if (timeDiffInSecond < 0) {
                return 0;
            } else {
                return (timeDiffInSecond / firstSlice.sliceWindowSizeInSecond) + 1;
            }
        }

        private final Instant startSliceInstant;
        private final long sliceWindowSizeInSecond;

        private Slice(Instant startSliceInstant, long sliceWindowSizeInSecond) {
            Preconditions.checkNotNull(startSliceInstant);
            Preconditions.checkArgument(sliceWindowSizeInSecond > 0, "sliceWindowSizeInSecond should be positive");

            this.startSliceInstant = startSliceInstant;
            this.sliceWindowSizeInSecond = sliceWindowSizeInSecond;
        }

        public Instant getStartSliceInstant() {
            return startSliceInstant;
        }

        public long getSliceWindowSizeInSecond() {
            return sliceWindowSizeInSecond;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Slice) {
                Slice slice = (Slice) o;

                return Objects.equals(this.sliceWindowSizeInSecond, slice.sliceWindowSizeInSecond)
                    && Objects.equals(this.startSliceInstant, slice.startSliceInstant);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(startSliceInstant, sliceWindowSizeInSecond);
        }
    }
}
