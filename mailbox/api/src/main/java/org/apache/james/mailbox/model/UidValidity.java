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

package org.apache.james.mailbox.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class UidValidity {
    public static UidValidity of(long uidValidity) {
        return new UidValidity(uidValidity);
    }

    private final long uidValidity;

    private UidValidity(long uidValidity) {
        this.uidValidity = uidValidity;
    }

    public long asLong() {
        return uidValidity;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof UidValidity) {
            UidValidity other = (UidValidity) obj;
            return Objects.equal(uidValidity, other.uidValidity);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(uidValidity);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("uidValidity", uidValidity)
            .toString();
    }
}
