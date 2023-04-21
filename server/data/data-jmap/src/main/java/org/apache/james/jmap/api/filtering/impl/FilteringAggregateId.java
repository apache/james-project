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

import java.util.Objects;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.AggregateId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class FilteringAggregateId implements AggregateId {
    private static final String SEPARATOR = "/";
    private static final String PREFIX = "FilteringRule";

    public static FilteringAggregateId parse(String rawString) {
        Preconditions.checkArgument(rawString.startsWith(PREFIX + SEPARATOR));
        return new FilteringAggregateId(Username.of(rawString.substring(PREFIX.length() + SEPARATOR.length())));
    }

    private final Username username;

    public FilteringAggregateId(Username username) {
        Preconditions.checkNotNull(username);

        this.username = username;
    }

    @Override
    public String asAggregateKey() {
        return PREFIX + SEPARATOR + username.asString();
    }

    public Username getUsername() {
        return username;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FilteringAggregateId) {
            FilteringAggregateId that = (FilteringAggregateId) o;

            return Objects.equals(this.username, that.username);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("user", username)
            .toString();
    }
}
