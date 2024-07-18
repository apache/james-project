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

package org.apache.james.server.blob.deduplication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.util.DurationParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class GenerationAwareBlobId implements BlobId {

    public static class Configuration {
        public static final Duration DEFAULT_DURATION = Duration.ofDays(30);
        public static final int DEFAULT_FAMILY = 1;
        public static final Configuration DEFAULT = builder()
            .duration(DEFAULT_DURATION)
            .family(DEFAULT_FAMILY);

        @FunctionalInterface
        public interface RequiresGenerationDuration {
            RequiresGenerationFamily duration(Duration duration);
        }

        @FunctionalInterface
        public interface RequiresGenerationFamily {
            Configuration family(int family);
        }

        public static RequiresGenerationDuration builder() {
            return duration -> family -> new Configuration(family, duration);
        }

        public static Configuration parse(org.apache.commons.configuration2.Configuration propertiesConfiguration) {
            return builder()
                .duration(Optional.ofNullable(propertiesConfiguration.getString("deduplication.gc.generation.duration", null))
                    .map(s -> DurationParser.parse(s, ChronoUnit.DAYS))
                    .orElse(DEFAULT_DURATION))
                .family(Optional.ofNullable(propertiesConfiguration.getString("deduplication.gc.generation.family", null))
                    .map(Integer::parseInt)
                    .orElse(DEFAULT_FAMILY));
        }

        private final int family;
        private final Duration duration;

        public Configuration(int family, Duration duration) {
            Preconditions.checkArgument(family > 0, "'family' must be strictly positive");
            Preconditions.checkNotNull(duration);
            Preconditions.checkArgument(!duration.isZero(), "'duration' must be strictly positive");

            this.family = family;
            this.duration = duration;
        }

        public int getFamily() {
            return family;
        }

        public Duration getDuration() {
            return duration;
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof Configuration) {
                Configuration other = (Configuration) obj;
                return Objects.equals(family, other.family)
                    && Objects.equals(duration, other.duration);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(family, duration);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("family", family)
                .add("duration", duration)
                .toString();
        }
    }

    public static class Factory implements BlobId.Factory {
        private final Clock clock;
        private final BlobId.Factory delegate;
        private final Configuration configuration;

        public Factory(Clock clock, BlobId.Factory delegate, Configuration configuration) {
            this.clock = clock;
            this.delegate = delegate;
            this.configuration = configuration;
        }

        @Override
        public GenerationAwareBlobId of(String id) {
            return decorate(delegate.of(id));
        }

        @Override
        public GenerationAwareBlobId parse(String id) {
            int separatorIndex1 = id.indexOf('_');
            if (separatorIndex1 == -1 || separatorIndex1 == id.length() - 1) {
                return decorateWithoutGeneration(id);
            }
            int separatorIndex2 = id.indexOf('_', separatorIndex1 + 1);
            if (separatorIndex2 == -1 || separatorIndex2 == id.length() - 1) {
                return decorateWithoutGeneration(id);
            }
            int family = Integer.parseInt(id.substring(0, separatorIndex1));
            int generation = Integer.parseInt(id.substring(separatorIndex1 + 1, separatorIndex2));
            BlobId wrapped = delegate.parse(id.substring(separatorIndex2 + 1));

            return new GenerationAwareBlobId(generation, family, wrapped);
        }

        private GenerationAwareBlobId decorateWithoutGeneration(String id) {
            return new GenerationAwareBlobId(NO_GENERATION, NO_FAMILY, delegate.parse(id));
        }

        private GenerationAwareBlobId decorate(BlobId blobId) {
            return new GenerationAwareBlobId(GenerationAwareBlobId.computeGeneration(configuration, clock.instant()),
                configuration.getFamily(),
                blobId);
        }
    }

    public static final int NO_FAMILY = 0;
    public static final int NO_GENERATION = 0;

    private static long computeGeneration(Configuration configuration, Instant now) {
        return now.getEpochSecond() / configuration.getDuration().toSeconds();
    }

    private final long generation;
    private final int family;
    private final BlobId delegate;

    @VisibleForTesting
    GenerationAwareBlobId(long generation, int family, BlobId delegate) {
        Preconditions.checkArgument(generation >= 0, "'generation' should not be negative");
        Preconditions.checkArgument(family >= 0, "'family' should not be negative");
        this.generation = generation;
        this.family = family;
        this.delegate = delegate;
    }

    @Override
    public String asString() {
        if (family == NO_FAMILY) {
            return delegate.asString();
        }
        return family + "_" + generation + "_" + delegate.asString();
    }

    public boolean inActiveGeneration(Configuration configuration, Instant now) {
        return configuration.getFamily() == this.family &&
            generation + 1 >= computeGeneration(configuration, now);
    }

    @VisibleForTesting
    long getGeneration() {
        return generation;
    }

    @VisibleForTesting
    int getFamily() {
        return family;
    }

    @VisibleForTesting
    BlobId getDelegate() {
        return delegate;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof GenerationAwareBlobId) {
            GenerationAwareBlobId other = (GenerationAwareBlobId) obj;
            return Objects.equals(generation, other.generation)
                && Objects.equals(delegate, other.delegate)
                && Objects.equals(family, other.family);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(generation, family, delegate);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("generation", generation)
            .add("delegate", delegate)
            .toString();
    }
}
