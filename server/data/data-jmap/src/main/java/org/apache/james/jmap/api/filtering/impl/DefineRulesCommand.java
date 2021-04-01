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

package org.apache.james.jmap.api.filtering.impl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.Command;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Version;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class DefineRulesCommand implements Command {

    private final Username username;
    private final List<Rule> rules;
    private final Optional<Version> ifInState;

    public DefineRulesCommand(Username username, List<Rule> rules, Optional<Version> ifInState) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(rules);

        this.username = username;
        this.rules = rules;
        this.ifInState = ifInState;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Optional<Version> getIfInState() {
        return ifInState;
    }

    public Username getUsername() {
        return username;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DefineRulesCommand) {
            DefineRulesCommand that = (DefineRulesCommand) o;

            return Objects.equals(this.username, that.username)
                && Objects.equals(this.rules, that.rules)
                && Objects.equals(this.ifInState, that.ifInState);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(username, rules, ifInState);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("user", username)
            .add("rules", rules)
            .add("ifInState", ifInState)
            .toString();
    }
}
