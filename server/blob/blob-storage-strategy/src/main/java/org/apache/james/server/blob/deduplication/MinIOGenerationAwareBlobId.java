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

import static org.apache.james.server.blob.deduplication.GenerationAwareBlobId.NO_FAMILY;
import static org.apache.james.server.blob.deduplication.GenerationAwareBlobId.computeGeneration;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class MinIOGenerationAwareBlobId implements BlobId, GenerationAware {
    public static class Factory implements BlobId.Factory {
        public static final int NO_FAMILY = 0;
        public static final int NO_GENERATION = 0;

        private final Clock clock;
        private final GenerationAwareBlobId.Configuration configuration;
        private final BlobId.Factory delegate;

        @Inject
        public Factory(Clock clock, GenerationAwareBlobId.Configuration configuration, BlobId.Factory delegate) {
            this.clock = clock;
            this.configuration = configuration;
            this.delegate = delegate;
        }

        @Override
        public BlobId parse(String id) {
            int separatorIndex1 = id.indexOf('/');
            if (separatorIndex1 == -1 || separatorIndex1 == id.length() - 1) {
                return decorateWithoutGeneration(id);
            }
            int separatorIndex2 = id.indexOf('/', separatorIndex1 + 1);
            if (separatorIndex2 == -1 || separatorIndex2 == id.length() - 1) {
                return decorateWithoutGeneration(id);
            }
            try {
                int family = Integer.parseInt(id.substring(0, separatorIndex1));
                int generation = Integer.parseInt(id.substring(separatorIndex1 + 1, separatorIndex2));
                if (family < 0 || generation < 0) {
                    return decorateWithoutGeneration(id);
                }
                BlobId wrapped = delegate.parse(id.substring(separatorIndex2 + 1));
                return new MinIOGenerationAwareBlobId(generation, family, wrapped);
            } catch (NumberFormatException e) {
                return delegate.parse(id.substring(separatorIndex2 + 1));
            }
        }

        private static String injectFoldersInBlobId(String blobIdPart) {
            int folderDepthToCreate = 2;
            if (blobIdPart.length() > folderDepthToCreate) {
                return blobIdPart.charAt(0) + "/" +
                    blobIdPart.charAt(1) + "/" +
                    blobIdPart.substring(2);
            }
            return blobIdPart;
        }

        private GenerationAwareBlobId decorateWithoutGeneration(String id) {
            return new GenerationAwareBlobId(NO_GENERATION, NO_FAMILY, delegate.parse(id));
        }

        @Override
        public BlobId of(String id) {
            return decorate(delegate.of(injectFoldersInBlobId(id)));
        }

        private MinIOGenerationAwareBlobId decorate(BlobId blobId) {
            return new MinIOGenerationAwareBlobId(computeGeneration(configuration, clock.instant()),
                configuration.getFamily(),
                blobId);
        }
    }

    private final long generation;
    private final int family;
    private final BlobId delegate;

    @VisibleForTesting
    MinIOGenerationAwareBlobId(long generation, int family, BlobId delegate) {
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
        return family + "/" + generation + "/" + delegate.asString();
    }

    @Override
    public boolean inActiveGeneration(GenerationAwareBlobId.Configuration configuration, Instant now) {
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
        if (obj instanceof MinIOGenerationAwareBlobId other) {
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
