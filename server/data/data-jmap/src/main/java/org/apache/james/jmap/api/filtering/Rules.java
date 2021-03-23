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

package org.apache.james.jmap.api.filtering;

import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class Rules {
    private final List<Rule> rules;
    private final Version version;

    public Rules(List<Rule> rules, Version version) {
        this.rules = rules;
        this.version = version;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Rules) {
            Rules that = (Rules) o;
            return Objects.equals(this.rules, that.rules)
                && Objects.equals(this.version, that.version);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rules, version);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("rules", rules)
            .add("version", version)
            .toString();
    }
}
