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
package org.apache.james.jmap.model.mailbox;

import com.google.common.collect.ImmutableMap;

import java.util.Optional;

public class SortOrder {

    private static final int DEFAULT_SORT_ORDER = 1000;
    private static final ImmutableMap<Role, Integer> defaultSortOrders =
            ImmutableMap.<Role, Integer>builder()
                .put(Role.INBOX, 10)
                .put(Role.ARCHIVE, 20)
                .put(Role.DRAFTS, 30)
                .put(Role.OUTBOX, 40)
                .put(Role.SENT, 50)
                .put(Role.TRASH, 60)
                .put(Role.SPAM, 70)
                .put(Role.TEMPLATES, 80)
                .build();

    private static Optional<Integer> getDefaultSortOrder(Role role) {
        return Optional.ofNullable(defaultSortOrders.get(role));
    }

    public static Integer getSortOrder(Optional<Role> role) {
        return role
                .map(SortOrder::getDefaultSortOrder)
                .map(Optional::get)
                .orElse(DEFAULT_SORT_ORDER);
    }
}
