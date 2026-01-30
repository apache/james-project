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

import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;

import com.google.common.base.Preconditions;

public record SearchOptions(Offset offset, Limit limit) {
    public static final SearchOptions FIRST = SearchOptions.limit(Limit.from(1));

    public static SearchOptions limit(Limit limit) {
        return SearchOptions.of(Offset.none(), limit);
    }

    public static SearchOptions of(Offset offset, Limit limit) {
        return new SearchOptions(offset, limit);
    }

    public SearchOptions {
        Preconditions.checkNotNull(offset, "'offset' is mandatory");
        Preconditions.checkNotNull(limit, "'limit' is mandatory");
        Preconditions.checkArgument(!limit.isUnlimited(), "'limit' cannot be unlimited");
    }
}
