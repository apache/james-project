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

package org.apache.james.util.streams;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class Offset {
    private static final Offset NONE = new Offset(0);

    public static Offset from(Optional<Integer> offset) {
        return offset.map(Offset::from)
            .orElse(NONE);
    }

    public static Offset none() {
        return NONE;
    }

    public static Offset from(int offset) {
        Preconditions.checkArgument(offset >= 0, "offset should be positive");
        return new Offset(offset);
    }

    private final int offset;

    private Offset(int offset) {
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Offset) {
            Offset other = (Offset) o;
            return Objects.equals(this.offset, other.offset);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(offset);
    }
}
