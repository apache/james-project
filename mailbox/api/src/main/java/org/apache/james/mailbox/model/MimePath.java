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

/**
 * 
 */
package org.apache.james.mailbox.model;

import java.util.Arrays;
import java.util.List;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;

/**
 * Describes a path within a multipart MIME message. All implementations
 * must implement equals. Two paths are equal if and only if each position
 * is identical.
 */
public final class MimePath {
    private final int[] positions;

    public MimePath(int[] positions) {
        this.positions = Arrays.copyOf(positions, positions.length);
    }

    /**
     * Gets the positions of each part in the path.
     *
     * @return part positions describing the path
     */
    public int[] getPositions() {
        return positions;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MimePath) {
            MimePath mimePath = (MimePath) o;

            return Arrays.equals(this.positions, mimePath.positions);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(positions);
    }

    @Override
    public final String toString() {
        List<Integer> parts = Arrays.stream(positions)
            .boxed()
            .collect(Guavate.toImmutableList());

        return "MIMEPath:"
            + Joiner.on('.')
            .join(parts);
    }
}