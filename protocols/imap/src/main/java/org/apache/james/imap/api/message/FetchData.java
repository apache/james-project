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
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

public class FetchData {
    private boolean flags;

    private boolean uid;

    private boolean internalDate;

    private boolean size;

    private boolean envelope;

    private boolean body;

    private boolean bodyStructure;

    private boolean setSeen = false;

    private final Set<BodyFetchElement> bodyElements = new HashSet<>();

    private boolean modSeq;

    private long changedSince = -1;

    private boolean vanished;

    public Collection<BodyFetchElement> getBodyElements() {
        return bodyElements;
    }

    public boolean isBody() {
        return body;
    }

    public FetchData setBody(boolean body) {
        this.body = body;
        return this;
    }

    public boolean isBodyStructure() {
        return bodyStructure;
    }

    public FetchData setBodyStructure(boolean bodyStructure) {
        this.bodyStructure = bodyStructure;
        return this;
    }

    public boolean isEnvelope() {
        return envelope;
    }

    public FetchData setEnvelope(boolean envelope) {
        this.envelope = envelope;
        return this;
    }

    public boolean isFlags() {
        return flags;
    }

    public FetchData setFlags(boolean flags) {
        this.flags = flags;
        return this;
    }

    public boolean isInternalDate() {
        return internalDate;
    }

    public FetchData setInternalDate(boolean internalDate) {
        this.internalDate = internalDate;
        return this;
    }

    public boolean isSize() {
        return size;
    }

    public FetchData setSize(boolean size) {
        this.size = size;
        return this;
    }

    public boolean isUid() {
        return uid;
    }

    public FetchData setUid(boolean uid) {
        this.uid = uid;
        return this;
    }

    public boolean isSetSeen() {
        return setSeen;
    }


    public boolean isModSeq() {
        return modSeq;
    }

    public FetchData setModSeq(boolean modSeq) {
        this.modSeq = modSeq;
        return this;
    }
    
    public FetchData setChangedSince(long changedSince) {
        this.changedSince = changedSince;
        this.modSeq = true;
        return this;
    }
    
    public long getChangedSince() {
        return changedSince;
    }
    
    /**
     * Set to true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC</code> extension
     * 
     * @param vanished
     */
    public FetchData setVanished(boolean vanished) {
        this.vanished = vanished;
        return this;
    }
    
    /**
     * Return true if the VANISHED FETCH modifier was used as stated in <code>QRESYNC<code> extension
     * 
     * @return vanished
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

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (body ? 1231 : 1237);
        result = PRIME * result + ((bodyElements == null) ? 0 : bodyElements.hashCode());
        result = PRIME * result + (bodyStructure ? 1231 : 1237);
        result = PRIME * result + (envelope ? 1231 : 1237);
        result = PRIME * result + (flags ? 1231 : 1237);
        result = PRIME * result + (internalDate ? 1231 : 1237);
        result = PRIME * result + (setSeen ? 1231 : 1237);
        result = PRIME * result + (size ? 1231 : 1237);
        result = PRIME * result + (uid ? 1231 : 1237);
        result = PRIME * result + (modSeq ? 1231 : 1237);
        result = (int) (PRIME * result + changedSince);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FetchData other = (FetchData) obj;
        if (body != other.body) {
            return false;
        }
        if (bodyElements == null) {
            if (other.bodyElements != null) {
                return false;
            }
        } else if (!bodyElements.equals(other.bodyElements)) {
            return false;
        }
        if (bodyStructure != other.bodyStructure) {
            return false;
        }
        if (envelope != other.envelope) {
            return false;
        }
        if (flags != other.flags) {
            return false;
        }
        if (internalDate != other.internalDate) {
            return false;
        }
        if (setSeen != other.setSeen) {
            return false;
        }
        if (size != other.size) {
            return false;
        }
        if (uid != other.uid) {
            return false;
        }
        if (modSeq != other.modSeq) {
            return false;
        }
        if (changedSince != other.changedSince) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("flags", flags)
            .add("uid", uid)
            .add("internalDate", internalDate)
            .add("size", size)
            .add("envelope", envelope)
            .add("body", body)
            .add("bodyStructure", bodyStructure)
            .add("setSeen", setSeen)
            .add("bodyElements", ImmutableSet.copyOf(bodyElements))
            .add("modSeq", modSeq)
            .add("changedSince", changedSince)
            .add("vanished", vanished)
            .toString();
    }
}
