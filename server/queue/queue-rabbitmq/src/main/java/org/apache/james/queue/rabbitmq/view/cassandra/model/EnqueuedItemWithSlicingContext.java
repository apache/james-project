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

import org.apache.james.queue.rabbitmq.EnqueuedItem;

import com.google.common.base.Preconditions;

public class EnqueuedItemWithSlicingContext {

    public static class SlicingContext {

        public static SlicingContext of(BucketedSlices.BucketId bucketId, Instant timeRangeStart) {
            return new SlicingContext(bucketId, timeRangeStart);
        }

        private final BucketedSlices.BucketId bucketId;
        private final Instant timeRangeStart;

        SlicingContext(BucketedSlices.BucketId bucketId, Instant timeRangeStart) {
            this.bucketId = bucketId;
            this.timeRangeStart = timeRangeStart;
        }

        public BucketedSlices.BucketId getBucketId() {
            return bucketId;
        }

        public Instant getTimeRangeStart() {
            return timeRangeStart;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof SlicingContext) {
                SlicingContext that = (SlicingContext) o;

                return Objects.equals(this.bucketId, that.bucketId)
                    && Objects.equals(this.timeRangeStart, that.timeRangeStart);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(bucketId, timeRangeStart);
        }
    }

    public interface Builder {

        @FunctionalInterface
        interface RequireEnqueuedItem {
            RequireSlicingContext enqueuedItem(EnqueuedItem enqueuedItem);
        }

        @FunctionalInterface
        interface RequireSlicingContext {
            ReadyToBuild slicingContext(SlicingContext slicingContext);
        }

        class ReadyToBuild {
            private final EnqueuedItem enqueuedItem;
            private final SlicingContext slicingContext;

            ReadyToBuild(EnqueuedItem enqueuedItem, SlicingContext slicingContext) {
                Preconditions.checkNotNull(enqueuedItem, "'enqueuedItem' is mandatory.");
                Preconditions.checkNotNull(slicingContext, "'slicingContext' is mandatory.");
                this.enqueuedItem = enqueuedItem;
                this.slicingContext = slicingContext;
            }

            public EnqueuedItemWithSlicingContext build() {
                return new EnqueuedItemWithSlicingContext(enqueuedItem, slicingContext);
            }
        }
    }

    public static Builder.RequireEnqueuedItem builder() {
        return enqueuedItem -> slicingContext -> new Builder.ReadyToBuild(enqueuedItem, slicingContext);
    }

    private final EnqueuedItem enqueuedItem;
    private final SlicingContext slicingContext;

    private EnqueuedItemWithSlicingContext(EnqueuedItem enqueuedItem, SlicingContext slicingContext) {
        this.enqueuedItem = enqueuedItem;
        this.slicingContext = slicingContext;
    }

    public SlicingContext getSlicingContext() {
        return slicingContext;
    }

    public EnqueuedItem getEnqueuedItem() {
        return enqueuedItem;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EnqueuedItemWithSlicingContext) {
            EnqueuedItemWithSlicingContext that = (EnqueuedItemWithSlicingContext) o;

            return Objects.equals(this.slicingContext, that.slicingContext)
                    && Objects.equals(this.enqueuedItem, that.enqueuedItem);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(slicingContext, enqueuedItem);
    }
}
