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

import org.apache.james.core.User;
import org.apache.james.eventsourcing.AggregateId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class FilteringAggregateId implements AggregateId {
    private static final String SEPARATOR = "/";
    private static final String PREFIX = "FilteringRule";

    public static final FilteringAggregateId parse(String rawString) {
        Preconditions.checkArgument(rawString.startsWith(PREFIX + SEPARATOR));
        return new FilteringAggregateId(User.fromUsername(rawString.substring(PREFIX.length() + SEPARATOR.length())));
    }

    private final User user;

    public FilteringAggregateId(User user) {
        Preconditions.checkNotNull(user);

        this.user = user;
    }

    @Override
    public String asAggregateKey() {
        return PREFIX + SEPARATOR + user.asString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FilteringAggregateId) {
            FilteringAggregateId that = (FilteringAggregateId) o;

            return Objects.equals(this.user, that.user);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(user);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("user", user)
            .toString();
    }
}
