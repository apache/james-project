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

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

public class FetchData {
    enum Item {
        FLAGS,
        UID,
        INTERNAL_DATE,
        SIZE,
        ENVELOPE,
        BODY,
        BODY_STRUCTURE,
        MODSEQ,
    }

    private final EnumSet<Item> itemToFetch = EnumSet.noneOf(Item.class);
    private final Set<BodyFetchElement> bodyElements = new HashSet<>();

    private boolean setSeen = false;
    private long changedSince = -1;
    private boolean vanished;

    public Collection<BodyFetchElement> getBodyElements() {
        return bodyElements;
    }

    public boolean isBody() {
        return itemToFetch.contains(Item.BODY);
    }

    public FetchData fetchBody() {
        itemToFetch.add(Item.BODY);
        return this;
    }

    public boolean isBodyStructure() {
        return itemToFetch.contains(Item.BODY_STRUCTURE);
    }

    public FetchData fetchBodyStructure() {
        itemToFetch.add(Item.BODY_STRUCTURE);
        return this;
    }

    public boolean isEnvelope() {
        return itemToFetch.contains(Item.ENVELOPE);
    }

    public FetchData fetchEnvelope() {
        itemToFetch.add(Item.ENVELOPE);
        return this;
    }

    public boolean isFlags() {
        return itemToFetch.contains(Item.FLAGS);
    }

    public FetchData fetchFlags() {
        itemToFetch.add(Item.FLAGS);
        return this;
    }

    public boolean isInternalDate() {
        return itemToFetch.contains(Item.INTERNAL_DATE);
    }

    public FetchData fetchInternalDate() {
        itemToFetch.add(Item.INTERNAL_DATE);
        return this;
    }

    public boolean isSize() {
        return itemToFetch.contains(Item.SIZE);
    }

    public FetchData fetchSize() {
        itemToFetch.add(Item.SIZE);
        return this;
    }

    public boolean isUid() {
        return itemToFetch.contains(Item.UID);
    }

    public FetchData fetchUid() {
        itemToFetch.add(Item.UID);
        return this;
    }

    public boolean isSetSeen() {
        return setSeen;
    }


    public boolean isModSeq() {
        return itemToFetch.contains(Item.MODSEQ);
    }

    public FetchData fetchModSeq() {
        itemToFetch.add(Item.MODSEQ);
        return this;
    }
    
    public FetchData setChangedSince(long changedSince) {
        this.changedSince = changedSince;
        itemToFetch.add(Item.MODSEQ);
        return this;
    }
    
    public long getChangedSince() {
        return changedSince;
    }
    
    /**
     * Set to true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC</code> extension
     */
    public FetchData setVanished(boolean vanished) {
        this.vanished = vanished;
        return this;
    }
    
    /**
     * Return true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC<code> extension
     */
    public boolean getVanished() {
        return vanished;
    }
    
    public FetchData add(BodyFetchElement element, boolean peek) {
        if (!peek) {
            setSeen = true;
        }
        bodyElements.add(element);
        return this;
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
        return MoreObjects.toStringHelper(this)
            .add("flags", isFlags())
            .add("uid", isUid())
            .add("internalDate", isInternalDate())
            .add("size", isSize())
            .add("envelope", isEnvelope())
            .add("body", isBody())
            .add("bodyStructure", isBodyStructure())
            .add("setSeen", setSeen)
            .add("bodyElements", ImmutableSet.copyOf(bodyElements))
            .add("modSeq", isModSeq())
            .add("changedSince", changedSince)
            .add("vanished", vanished)
            .toString();
    }
}
