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

import static org.apache.james.droplists.api.DeniedEntityType.DOMAIN;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryDropList implements DropList {

    private final Multimap<String, DropListEntry> dropList = Multimaps.synchronizedMultimap(HashMultimap.create());

    @Override
    public Mono<Void> add(DropListEntry entry) {
        dropList.put(entry.getOwnerScope().name(), entry);
        return Mono.empty();
    }

    @Override
    public Mono<Void> remove(DropListEntry entry) {
        dropList.remove(entry.getOwnerScope().name(), entry);
        return Mono.empty();
    }

    @Override
    public Flux<DropListEntry> list(OwnerScope ownerScope, String owner) {
        return Flux.fromIterable(dropList.get(ownerScope.name()).stream()
            .filter(entry -> entry.getOwner().equals(owner))
            .toList());
    }

    @Override
    public Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender) {
        boolean isBlocked = dropList.get(ownerScope.name()).stream()
            .anyMatch(entry -> isEntryMatchingOwnerAndDeniedEntity(owner, sender, entry));

        return Mono.just(isBlocked ? Status.BLOCKED : Status.ALLOWED);
    }

    private static boolean isEntryMatchingOwnerAndDeniedEntity(String owner, MailAddress sender, DropListEntry entry) {
        String entityFromSender;
        String deniedEntity;
        if (entry.getOwnerScope().equals(OwnerScope.GLOBAL)) {
            entityFromSender = sender.getDomain().asString();
            try {
                deniedEntity = (entry.getDeniedEntityType().equals(DOMAIN)) ? Domain.of(entry.getDeniedEntity()).asString() :
                    new MailAddress(entry.getDeniedEntity()).getDomain().asString();
                return entry.getOwner().equals(owner) && deniedEntity.equals(entityFromSender);
            } catch (AddressException e) {
                return false;
            }
        } else {
            entityFromSender = (entry.getDeniedEntityType().equals(DOMAIN)) ? sender.getDomain().asString() : sender.asString();
            deniedEntity = (entry.getDeniedEntityType().equals(DOMAIN)) ? Domain.of(entry.getDeniedEntity()).asString() : entry.getDeniedEntity();
            return entry.getOwner().equals(owner) && deniedEntity.equals(entityFromSender);
        }
    }
}
