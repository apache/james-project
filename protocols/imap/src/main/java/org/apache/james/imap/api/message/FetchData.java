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
package org.apache.james.imap.api.message;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

public class FetchData {
    public static class Builder {
        public static Builder from(FetchData fetchData) {
            return builder()
                .fetch(fetchData.itemToFetch)
                .vanished(fetchData.vanished)
                .changedSince(fetchData.changedSince)
                .addBodyElements(fetchData.bodyElements)
                .seen(fetchData.setSeen);
        }

        private final EnumSet<Item> itemToFetch = EnumSet.noneOf(Item.class);
        private final ImmutableSet.Builder<BodyFetchElement> bodyElements = ImmutableSet.builder();
        private boolean setSeen = false;
        private long changedSince = -1;
        private boolean vanished;
        private Optional<PartialRange> partialRange = Optional.empty();

        public Builder fetch(Item item) {
            itemToFetch.add(item);
            return this;
        }

        public Builder fetch(Item... item) {
            return fetch(Arrays.asList(item));
        }

        public Builder fetch(Collection<Item> items) {
            itemToFetch.addAll(items);
            return this;
        }

        public Builder changedSince(long changedSince) {
            this.changedSince = changedSince;
            return fetch(Item.MODSEQ);
        }

        public Builder partial(PartialRange partialRange) {
            this.partialRange = Optional.of(partialRange);
            return this;
        }

        /**
         * Set to true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC</code> extension
         */
        public Builder vanished(boolean vanished) {
            this.vanished = vanished;
            return this;
        }

        public Builder add(BodyFetchElement element, boolean peek) {
            if (!peek) {
                setSeen = true;
            }
            bodyElements.add(element);
            return this;
        }

        private Builder addBodyElements(Collection<BodyFetchElement> elements) {
            bodyElements.addAll(elements);
            return this;
        }

        private Builder seen(boolean setSeen) {
            this.setSeen = setSeen;
            return this;
        }

        public FetchData build() {
            return new FetchData(itemToFetch, bodyElements.build(), partialRange, setSeen, changedSince, vanished);
        }
    }

    public enum Item {
        FLAGS,
        UID,
        INTERNAL_DATE,
        SIZE,
        ENVELOPE,
        BODY,
        BODY_STRUCTURE,
        MODSEQ,
        // https://www.rfc-editor.org/rfc/rfc8474.html#section-5.3
        EMAILID,
        THREADID,
        // https://www.rfc-editor.org/rfc/rfc8514.html#section-4.2
        SAVEDATE
    }

    public static Builder builder() {
        return new Builder();
    }

    private final EnumSet<Item> itemToFetch;
    private final ImmutableSet<BodyFetchElement> bodyElements;
    private final Optional<PartialRange> partialRange;
    private final boolean setSeen;
    private final long changedSince;
    private final boolean vanished;

    private FetchData(EnumSet<Item> itemToFetch, ImmutableSet<BodyFetchElement> bodyElements, Optional<PartialRange> partialRange, boolean setSeen, long changedSince, boolean vanished) {
        this.itemToFetch = itemToFetch;
        this.bodyElements = bodyElements;
        this.partialRange = partialRange;
        this.setSeen = setSeen;
        this.changedSince = changedSince;
        this.vanished = vanished;
    }

    public Collection<BodyFetchElement> getBodyElements() {
        return bodyElements;
    }

    public boolean contains(Item item) {
        return itemToFetch.contains(item);
    }

    public boolean isSetSeen() {
        return setSeen;
    }
    
    public long getChangedSince() {
        return changedSince;
    }

    public Optional<PartialRange> getPartialRange() {
        return partialRange;
    }

    /**
     * Return true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC<code> extension
     */
    public boolean getVanished() {
        return vanished;
    }

    public boolean isOnlyFlags() {
        return bodyElements.isEmpty()
            && itemToFetch.stream()
            .filter(item -> item != Item.FLAGS)
            .filter(item -> item != Item.UID)
            .filter(item -> item != Item.MODSEQ)
            .findAny()
            .isEmpty();
    }

    @Override
    public final int hashCode() {
        return Objects.hash(itemToFetch, bodyElements, setSeen, changedSince);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FetchData) {
            FetchData fetchData = (FetchData) o;

            return Objects.equals(this.setSeen, fetchData.setSeen)
                && Objects.equals(this.changedSince, fetchData.changedSince)
                && Objects.equals(this.itemToFetch, fetchData.itemToFetch)
                && Objects.equals(this.bodyElements, fetchData.bodyElements);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FetchData.class.getSimpleName())
            .omitNullValues()
            .add("items", itemToFetch)
            .add("setSeen", setSeen)
            .add("bodyElements", bodyElements)
            .add("changedSince", changedSince)
            .add("vanished", vanished)
            .toString();
    }
}
