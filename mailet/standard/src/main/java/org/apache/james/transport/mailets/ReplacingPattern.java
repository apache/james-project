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

package org.apache.james.transport.mailets;

import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class ReplacingPattern {

    private final Pattern matcher;
    private final boolean repeat;
    private final String substitution;

    public ReplacingPattern(Pattern matcher, boolean repeat, String substitution) {
        this.matcher = matcher;
        this.repeat = repeat;
        this.substitution = substitution;
    }

    public Pattern getMatcher() {
        return matcher;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public String getSubstitution() {
        return substitution;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ReplacingPattern) {
            ReplacingPattern other = (ReplacingPattern) o;
            return Objects.equal(matcher.pattern(), other.matcher.pattern())
                && Objects.equal(repeat, other.repeat)
                && Objects.equal(substitution, other.substitution);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(matcher, repeat, substitution);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("matcher", matcher)
            .add("repeat", repeat)
            .add("substitution", substitution)
            .toString();
    }
}