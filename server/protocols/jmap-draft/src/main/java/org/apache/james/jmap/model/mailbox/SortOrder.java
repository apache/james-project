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

import java.util.Optional;

import org.apache.james.mailbox.Role;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class SortOrder implements Comparable<SortOrder> {

    private static final SortOrder DEFAULT_SORT_ORDER = SortOrder.of(1000);
    private static final ImmutableMap<Role, SortOrder> defaultSortOrders =
            ImmutableMap.<Role, SortOrder>builder()
                .put(Role.INBOX, SortOrder.of(10))
                .put(Role.ARCHIVE, SortOrder.of(20))
                .put(Role.DRAFTS, SortOrder.of(30))
                .put(Role.OUTBOX, SortOrder.of(40))
                .put(Role.SENT, SortOrder.of(50))
                .put(Role.TRASH, SortOrder.of(60))
                .put(Role.SPAM, SortOrder.of(70))
                .put(Role.TEMPLATES, SortOrder.of(80))
                .put(Role.RESTORED_MESSAGES, SortOrder.of(90))
                .build();

    private static Optional<SortOrder> getDefaultSortOrder(Role role) {
        return Optional.ofNullable(defaultSortOrders.get(role));
    }

    public static SortOrder getSortOrder(Optional<Role> role) {
        return role
                .map(SortOrder::getDefaultSortOrder)
                .map(Optional::get)
                .orElse(DEFAULT_SORT_ORDER);
    }

    public static SortOrder of(int sortOrder) {
        Preconditions.checkArgument(sortOrder >= 0, "'sortOrder' must be positive");
        return new SortOrder(sortOrder);
    }

    private final int sortOrder;

    private SortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @JsonValue
    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public int compareTo(SortOrder o) {
        return Integer.compare(sortOrder, o.sortOrder);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass()).add("order", sortOrder).toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SortOrder) {
            return sortOrder == ((SortOrder)obj).sortOrder;
        }
        return super.equals(obj);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(sortOrder);
    }
}
