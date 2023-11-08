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

package org.apache.james.mailbox;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class ModSeq implements Comparable<ModSeq> {
    public static ModSeq of(long modSeq) {
        return new ModSeq(modSeq);
    }

    public static ModSeq first() {
        return of(0L);
    }

    private final long modSeq;

    private ModSeq(long modSeq) {
        Preconditions.checkArgument(modSeq >= 0, "A modseq needs to be positive");
        this.modSeq = modSeq;
    }

    public long asLong() {
        return modSeq;
    }

    public ModSeq next() {
        if (modSeq == Long.MAX_VALUE) {
            throw new RuntimeException("Long overflow upon modseq generation");
        }
        return new ModSeq(modSeq + 1);
    }

    public boolean isFirst() {
        return this.equals(ModSeq.first());
    }

    public ModSeq add(int offset) {
        return ModSeq.of(modSeq + offset);
    }

    @Override
    public int compareTo(ModSeq o) {
        return Long.compare(modSeq, o.modSeq);
    }
    
    @Override
    public final int hashCode() {
        return Long.hashCode(modSeq);
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ModSeq) {
            ModSeq other = (ModSeq) obj;
            return other.modSeq == this.modSeq;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", modSeq)
            .toString();
    }
}
