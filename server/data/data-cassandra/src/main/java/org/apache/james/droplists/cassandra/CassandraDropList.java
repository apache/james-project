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
package org.apache.james.droplists.cassandra;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDropList implements DropList {

    private final CassandraGlobalDropListDAO globalDropListDAO;
    private final CassandraDomainDropListDAO domainDropListDAO;
    private final CassandraUserDropListDAO userDropListDAO;

    @Inject
    public CassandraDropList(CassandraGlobalDropListDAO globalDropListDAO, CassandraDomainDropListDAO domainDropListDAO,
                             CassandraUserDropListDAO userDropListDAO) {
        this.globalDropListDAO = globalDropListDAO;
        this.domainDropListDAO = domainDropListDAO;
        this.userDropListDAO = userDropListDAO;
    }

    @Override
    public Mono<Void> add(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        return switch (entry.getOwnerScope()) {
            case GLOBAL -> globalDropListDAO.addDropList(entry);
            case DOMAIN -> domainDropListDAO.addDropList(entry);
            case USER -> userDropListDAO.addDropList(entry);
        };
    }

    @Override
    public Mono<Void> remove(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        return switch (entry.getOwnerScope()) {
            case GLOBAL -> globalDropListDAO.removeDropList(entry);
            case DOMAIN -> domainDropListDAO.removeDropList(entry);
            case USER -> userDropListDAO.removeDropList(entry);
        };
    }

    @Override
    public Flux<DropListEntry> list(OwnerScope ownerScope, String owner) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        return switch (ownerScope) {
            case GLOBAL -> globalDropListDAO.getDropList();
            case DOMAIN -> domainDropListDAO.getDropList(owner);
            case USER -> userDropListDAO.getDropList(owner);
        };
    }

    @Override
    public Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        Preconditions.checkArgument(sender != null);
        return switch (ownerScope) {
            case GLOBAL -> globalDropListDAO.queryDropList(sender);
            case DOMAIN -> domainDropListDAO.queryDropList(owner, sender);
            case USER -> userDropListDAO.queryDropList(owner, sender);
        };
    }
}