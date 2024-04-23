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
package org.apache.james.droplists.memory;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DeniedEntityType;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryDropList implements DropList {

    private final Multimap<OwnerScope, DropListEntry> globalDropList = Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Multimap<OwnerScope, DropListEntry> domainDropList = Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Multimap<OwnerScope, DropListEntry> userDropList = Multimaps.synchronizedMultimap(HashMultimap.create());

    @Override
    public Mono<Void> add(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        OwnerScope ownerScope = entry.getOwnerScope();
        Multimap<OwnerScope, DropListEntry> selectedDropList = getDropListByScope(ownerScope);
        return Mono.fromRunnable(() -> selectedDropList.put(ownerScope, entry));
    }

    @Override
    public Mono<Void> remove(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        OwnerScope ownerScope = entry.getOwnerScope();
        Multimap<OwnerScope, DropListEntry> selectedDropList = getDropListByScope(ownerScope);
        return Mono.fromRunnable(() -> selectedDropList.remove(ownerScope, entry));
    }

    @Override
    public Flux<DropListEntry> list(OwnerScope ownerScope, String owner) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        Multimap<OwnerScope, DropListEntry> selectedDropList = getDropListByScope(ownerScope);
        return Flux.fromIterable(selectedDropList.get(ownerScope))
            .filter(entry -> entry.getOwner().equals(owner));
    }

    @Override
    public Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        Preconditions.checkArgument(sender != null);
        Multimap<OwnerScope, DropListEntry> selectedDropList = getDropListByScope(ownerScope);
        boolean isBlocked = selectedDropList.get(ownerScope).stream()
            .anyMatch(entry -> isEntryMatchingOwner(owner, entry) && isEntryMatchingDeniedEntity(sender, entry));

        return Mono.just(isBlocked ? Status.BLOCKED : Status.ALLOWED);
    }

    private Multimap<OwnerScope, DropListEntry> getDropListByScope(OwnerScope ownerScope) {
        return switch (ownerScope) {
            case GLOBAL -> globalDropList;
            case DOMAIN -> domainDropList;
            case USER -> userDropList;
        };
    }

    private boolean isEntryMatchingOwner(String owner, DropListEntry entry) {
        return entry.getOwner().equals(owner);
    }

    private boolean isEntryMatchingDeniedEntity(MailAddress sender, DropListEntry entry) {
        String entityFromSender = entry.getDeniedEntityType() == DeniedEntityType.DOMAIN ? sender.getDomain().asString() : sender.asString();

        return entry.getDeniedEntity().equals(entityFromSender);
    }
}